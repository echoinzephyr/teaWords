package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val courseStages = listOf("先听懂情境", "理解这次交流", "学关键表达", "带提示练习", "换个情境用", "听写回顾", "本课复盘")
private fun questionStage(phase: String) = when(phase) {
    "understand" -> 1; "guided" -> 3; "transfer" -> 4; else -> 5
}

@Composable
internal fun CourseLessonScreen(helper: DatabaseHelper, lesson: CourseLesson, onBack: () -> Unit) {
    var reference by rememberSaveable(lesson.id) { mutableStateOf(false) }
    var segment by rememberSaveable(lesson.id) { mutableStateOf<String?>(null) }
    if (reference) PredictiveBackLayer(onBack = { reference = false }) { back ->
        CourseReferenceScreen(helper, lesson, back, segment)
    } else GuidedCourseLesson(helper, lesson, onBack) { id -> segment = id; reference = true }
}

/** One activity at a time. Reference material is available without replacing the learning path. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidedCourseLesson(helper: DatabaseHelper, lesson: CourseLesson, onBack: () -> Unit,
                               onReference: (String?) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val repository = remember(helper) { CourseRepository(helper) }
    var progress by remember(lesson.id) { mutableStateOf(repository.progress(lesson.id)) }
    var state by remember(lesson.id) { mutableStateOf(progress.checkpoint.let { old ->
        if (old.guideVersion == 2) old else old.copy(guideVersion=2,
            guidedStage=if(progress.attemptedAll)6 else 0,question=0,point=0,draft="",drafts=emptyMap(),
            roundIds=emptyList(),roundDone=emptyList(),reviewRound=false)
    }) }
    var saveError by remember { mutableStateOf<String?>(null) }
    fun persist() {
        state=state.copy(guideVersion=2,started=true)
        try { repository.save(lesson.id,state);saveError=null }
        catch (_: Exception) { saveError="学习记录未保存，请重试" }
    }
    val player=rememberCourseAudioPlayer(lesson) { value ->
        state=state.copy(positionMs=value.position,sentence=value.sentence,speed=value.speed);persist()
    }
    val media=remember { CourseMedia(context) }
    var file by remember(lesson.id) { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var heard by remember { mutableStateOf(false) }
    val stage=state.guidedStage.coerceIn(0,6)
    val questions=lesson.questions.filter { questionStage(it.phase)==stage &&
        (state.roundIds.isEmpty() || it.id in state.roundIds) }
    val question=questions.getOrNull(state.question.coerceAtMost(questions.lastIndex))
    val answer=question?.let { progress.answers[it.id] }
    val feedback=answer!=null && (state.roundIds.isEmpty() || question!!.id in state.roundDone)
    val typed=question?.let { state.drafts[it.id] }.orEmpty()
    val guide=lesson.teaching

    LaunchedEffect(lesson.id) {
        file=withContext(Dispatchers.IO){media.cached(lesson.audio)}
        player.speed=state.speed
        file?.let { player.prepare(it,state.positionMs) }
        persist()
    }
    LaunchedEffect(stage,question?.id,state.roundIds) { heard=false }
    LaunchedEffect(player.playing) { while(player.playing) {
        delay(1000);state=state.copy(positionMs=player.position,sentence=player.sentence,speed=player.speed);persist()
    } }
    LaunchedEffect(stage,progress.complete,state.roundDone.size) {
        if(stage==6) {
            val finishedRound=state.roundIds.isNotEmpty() && state.roundIds.all{it in state.roundDone}
            if(progress.complete && (state.reviewAt==0L || finishedRound && state.reviewRound)) {
                val reviews=state.reviewCount+if(state.reviewRound)1 else 0
                state=state.copy(reviewAt=CourseReviewTiming.next(System.currentTimeMillis(),reviews),reviewCount=reviews)
                persist()
            }
            if(finishedRound) {state=state.copy(roundIds=emptyList(),roundDone=emptyList(),reviewRound=false);persist()}
        }
    }
    fun download() {
        downloadJob?.cancel()
        downloadJob=scope.launch {
            downloading=true;mediaError=null
            try {
                file=media.download(lesson.audio){done,total->downloadProgress=done.toFloat()/total}
                file?.let{player.prepare(it,state.positionMs)}
            } catch(cancelled:CancellationException){throw cancelled}
            catch (_:Exception){mediaError="原声暂未下载完成。文字和答案会保留，可以重试。"}
            finally{downloading=false}
        }
    }
    fun move(next:Int,number:Int=0) {
        player.pause();focus.clearFocus();keyboard?.hide();heard=false
        state=state.copy(guidedStage=next,question=number,positionMs=player.position);persist()
    }
    fun startRound(ids:List<String>,review:Boolean=false) {
        if(ids.isEmpty()) return
        val first=lesson.questions.first{it.id in ids}
        state=state.copy(roundIds=ids,roundDone=emptyList(),reviewRound=review,
            drafts=state.drafts.filterKeys{it !in ids})
        move(questionStage(first.phase))
    }
    fun advance() {
        if(stage==2 && state.point<guide.points.lastIndex) {state=state.copy(point=state.point+1);persist();return}
        if(question!=null && state.question<questions.lastIndex){move(stage,state.question+1);return}
        val next=if(state.roundIds.isEmpty())stage+1 else lesson.questions
            .filter{it.id in state.roundIds && it.id !in state.roundDone}.minOfOrNull{questionStage(it.phase)} ?: 6
        move(next.coerceAtMost(6))
    }
    fun submit() {
        val q=question ?: return
        player.pause();focus.clearFocus();keyboard?.hide()
        try {
            progress=repository.submit(lesson,q.id,typed,answer?.attempt ?: 0)
            state=state.copy(drafts=state.drafts-q.id,roundDone=(state.roundDone+q.id).distinct())
            persist()
        } catch (_:Exception){saveError="答案未保存，请重试"}
    }
    fun reference() {
        player.pause()
        if(question!=null && !feedback) state=state.copy(hinted=(state.hinted+question.id).distinct())
        state=state.copy(positionMs=player.position);persist()
        onReference(question?.segmentId)
    }
    val unfinished=lesson.questions.firstOrNull{it.id !in progress.answers}
    TeaExercisePage(title="第 ${lesson.number} 课 · ${courseStages[stage]}",done=stage+1,total=courseStages.size,
        busy=false,onBack={player.pause();persist();onBack()},actions={
            saveError?.let { Text(it,color=MaterialTheme.colorScheme.error)
                TextButton(onClick={persist()}){Text("重试保存")} }
            when {
                stage==6 && unfinished!=null -> TeaExerciseAction("继续未完成练习") {
                    move(questionStage(unfinished.phase),lesson.questions.filter{it.phase==unfinished.phase}.indexOf(unfinished))
                }
                stage==6 && !progress.complete -> TeaExerciseAction("巩固 ${progress.mistakes.size} 道错题") {
                    startRound(lesson.questions.filter{it.id in progress.mistakes}.map{it.id})
                }
                stage==6 -> TeaExerciseAction("返回课程") {onBack()}
                stage==0 -> TeaExerciseAction("开始学习") {move(1)}
                stage==2 -> TeaExerciseAction(if(state.point<guide.points.lastIndex)"下一个表达" else "带着提示试一试") {advance()}
                question!=null && !feedback -> TeaExerciseAction("提交答案",
                    enabled=typed.isNotBlank() && (question.type!="dictation" || heard && player.error==null)) {submit()}
                else -> TeaExerciseAction("继续") {advance()}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                if(stage>0 && stage<6 && state.roundIds.isEmpty()) TextButton(onClick={
                    if(question!=null && state.question>0)move(stage,state.question-1) else move(stage-1)
                }){Text("上一步")}
                TextButton(onClick={reference()}){Text("原文与词句笔记")}
            }
        }) {
        when(stage) {
            0 -> {
                item { Text(lesson.title,style=MaterialTheme.typography.headlineSmall)
                    Text(guide.mission,style=MaterialTheme.typography.titleMedium) }
                item { TeaCard { TeaCaption("你要面对的情境");Text(guide.situation)
                    Text(guide.listenFor,color=MaterialTheme.colorScheme.primary) } }
                item { GuidedAudioControls(player,downloading,downloadProgress,mediaError,file!=null,
                    "听完整对话",onPlay={player.play()},onDownload={if(downloading)downloadJob?.cancel() else download()}) }
                item { TeaCaption("先听一遍，抓住交流的目的。需要时可以读原文；接着会有两道理解题。") }
            }
            2 -> {
                val point=guide.points[state.point.coerceAtMost(guide.points.lastIndex)]
                item { TeaCaption("关键表达 ${state.point+1} / ${guide.points.size}")
                    Text(point.expression,style=MaterialTheme.typography.headlineSmall)
                    Text(point.meaning,style=MaterialTheme.typography.titleMedium) }
                item { TeaCard { Text("什么时候用",fontWeight=FontWeight.Bold);Text(point.useWhen)
                    HorizontalDivider();Text("容易混淆的地方",fontWeight=FontWeight.Bold);Text(point.contrast) } }
                item { TeaCard(containerColor=MaterialTheme.colorScheme.secondaryContainer) {
                    TeaCaption("换个例子");Text(point.example,style=MaterialTheme.typography.titleLarge)
                    TextButton(enabled=player.ready,onClick={player.play(lesson.segments.indexOfFirst{it.id==point.segmentId})}) {
                        Text("听课文里的用法")
                    }
                } }
            }
            6 -> {
                item { Text(when {
                    !progress.attemptedAll -> "还有练习没完成"
                    !progress.complete -> "练过了，再巩固一下"
                    else -> "本轮已过关"
                },style=MaterialTheme.typography.headlineSmall)
                    Text("${progress.answers.values.count{it.correct}} / ${lesson.questions.size} 题答对")
                    TeaCaption("做完不等于掌握。换个时间再试，看看还能不能自己答出来。") }
                item { TeaCard {
                    listOf("understand" to "听懂情境","guided" to "带提示练","transfer" to "换情境用","listen" to "听写回顾").forEach{(phase,label)->
                        val items=lesson.questions.filter{it.phase==phase}
                        Text("$label：${items.count{progress.answers[it.id]?.correct==true}} / ${items.size}")
                    }
                    if(state.hinted.isNotEmpty())TeaCaption("练习中有 ${state.hinted.size} 题查看过提示或资料。")
                    if(progress.complete && state.reviewAt>0)Text("下次回忆：${SimpleDateFormat("M月d日",Locale.CHINA).format(Date(state.reviewAt))}")
                } }
                item { TeaCard {
                    Text("现在由你来说",style=MaterialTheme.typography.titleMedium)
                    Text(guide.speakingTask)
                    CourseVoiceRecorder("${lesson.id}-speaking",onBeforeRecord={player.pause()})
                    var example by remember { mutableStateOf(false) }
                    TextButton(onClick={example=!example}){Text(if(example)"收起示范" else "说完后看一种示范")}
                    if(example)Text(guide.speakingExample,color=MaterialTheme.colorScheme.primary)
                    TeaCaption("回听时自己检查是否表达清楚；录音不计为已掌握，也不自动评分。")
                } }
                item { Text(guide.takeaway)
                    if(progress.complete)OutlinedButton(onClick={startRound(lesson.questions.filter{it.phase in setOf("transfer","listen")}.map{it.id},true)}) {
                        Text("不看讲解，再试一次")
                    }
                }
            }
            else -> question?.let { q ->
                item { TeaCaption(when(q.phase){
                    "guided"->"可以对照刚学的规则。";"transfer"->"人物或地点变了，试着自己组织一句。"
                    "listen"->"收起文字，只听这次原声。";else->"先抓住意思，再看细节。"
                })
                    Text("${state.question+1} / ${questions.size}",style=MaterialTheme.typography.labelLarge)
                    Text(q.prompt,style=MaterialTheme.typography.titleLarge) }
                if(q.type=="dictation")item {
                    GuidedAudioControls(player,downloading,downloadProgress,mediaError,file!=null,"听题目原声",
                        onPlay={heard=player.play(lesson.segments.indexOfFirst{it.id==q.segmentId})},
                        onDownload={if(downloading)downloadJob?.cancel() else download()})
                    if(!player.ready)TeaCaption("原声不可用时题目和草稿仍会保存，下载后再听写。")
                }
                if(!feedback) {
                    if(q.options.isNotEmpty())q.options.forEach{option->item {
                        TeaExerciseOption(if(typed==option)"✓ $option" else option){state=state.copy(drafts=state.drafts+(q.id to option));persist()}
                    }} else item {
                        OutlinedTextField(value=typed,onValueChange={state=state.copy(drafts=state.drafts+(q.id to it));persist()},
                            modifier=Modifier.fillMaxWidth(),label={Text("你的答案")},minLines=1,maxLines=3)
                    }
                    if(q.hint.isNotBlank())item {
                        if(q.id in state.hinted)TeaCaption(q.hint)
                        else TextButton(onClick={state=state.copy(hinted=(state.hinted+q.id).distinct());persist()}){Text("需要一点提示")}
                    }
                } else item {
                    TeaCard(containerColor=if(answer!!.correct)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text(if(answer.correct)"这次答对了" else "看看这个区别",fontWeight=FontWeight.Bold)
                        Text("你的答案：${answer.input}")
                        Text("可以这样表达：${q.answers.joinToString(" / ")}")
                        Text(q.explanation)
                        if(q.type=="dictation")Text(lesson.segments.single{it.id==q.segmentId}.chinese)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidedAudioControls(player:CourseAudioPlayer,loading:Boolean,progress:Float,error:String?,
                                cached:Boolean,label:String,onPlay:()->Unit,onDownload:()->Unit) {
    var menu by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(cached)Button(enabled=player.ready,onClick={if(player.playing)player.pause() else onPlay()}) {
            Text(if(player.playing)"暂停" else label)
        } else Button(onClick=onDownload){Text(if(loading)"暂停下载" else "下载本课原声")}
        Box {
            TextButton(onClick={menu=true}){Text("${player.speed}×")}
            DropdownMenu(menu,{menu=false}){listOf(.75f,1f,1.25f,1.5f).forEach{speed->
                DropdownMenuItem(text={Text("${speed}×")},onClick={player.changeSpeed(speed);menu=false})
            }}
        }
    }
    if(loading)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
    (error ?: player.error)?.let { Text(it,color=MaterialTheme.colorScheme.error)
        TextButton(onClick=onDownload){Text("重试原声")} }
}
