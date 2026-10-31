package com.teameow.teawords.data

/** Original, offline exam-style pilot content. These are practice texts, not past examination items. */
data class CourseLesson(
    val id: String,
    val title: String,
    val words: List<String>,
    val focus: String,
    val meaning: String,
    val example: String,
    val collocation: String,
    val wordPart: String,
    val passage: String,
    val readingQuestion: String,
    val readingAnswer: String,
    val readingDistractors: List<String>,
    val orderedSentence: String,
    val writingPrompt: String,
    val modelWriting: String
) {
    val source: String get() = "茶词原创练习 · 非真题"
    val bookId: String get() = id.substringBefore('-')
}

object CourseCatalog {
    const val CONTENT_VERSION = 1
    private fun lesson(n: Int, title: String, words: String, focus: String, meaning: String,
                       example: String, collocation: String, wordPart: String, passage: String,
                       question: String, answer: String, wrongA: String, wrongB: String,
                       order: String, writing: String, model: String) = CourseLesson(
        "ky-%02d".format(n), title, words.split(' ').filter { it.isNotBlank() }, focus, meaning,
        example, collocation, wordPart, passage, question, answer, listOf(wrongA, wrongB),
        order, writing, model)

    val postgraduate: List<CourseLesson> = listOf(
        lesson(1, "研究与证据",
            "hypothesis evidence analyze assess method data survey sample variable outcome reliable valid bias objective estimate infer conclude observe interpret experiment theory framework context evaluate review confirm reject abstract analogy assumption attribute category concept conduct contrast",
            "evidence", "证据；依据", "The evidence supports a cautious conclusion.", "strong evidence", "e-（向外）+ vid（看）：可看见的依据",
            "A research team compared two study methods. The first group improved, but the evidence was limited because the sample was small. The team asked for a larger trial before making a public claim.",
            "Why did the team avoid a firm claim?", "The sample was small.", "The methods were identical.", "The study had no participants.",
            "The evidence supports a cautious conclusion.", "用英语说明为什么一项研究需要足够大的样本。", "A larger sample can make a finding more reliable."),
        lesson(2, "教育与学习",
            "education curriculum literacy academic discipline lecture seminar tutor pupil graduate enroll campus scholarship tuition skill competence motivation cognitive memorize recall practice feedback achievement performance assessment certificate inquiry critical ability adequate advocate anticipate appropriate aware",
            "curriculum", "课程体系", "The curriculum combines reading with discussion.", "school curriculum", "curr（跑、进程）+ -iculum：学习进程",
            "A college revised its curriculum after surveying graduates. Employers valued clear writing and teamwork as well as technical knowledge. The new courses therefore included short projects with regular feedback.",
            "What did employers value besides technical knowledge?", "Writing and teamwork.", "Longer examinations.", "Lower tuition only.",
            "The curriculum combines reading with discussion.", "用英语说明及时反馈怎样帮助学习。", "Timely feedback helps students identify and correct mistakes."),
        lesson(3, "环境与能源",
            "environment sustainable climate carbon emission renewable resource ecosystem biodiversity habitat species conserve conservation pollution waste recycle efficiency energy solar wind fossil fuel drought flood temperature adapt resilience footprint alternative capacity collapse",
            "conservation", "保护；节约", "The city needs a conservation plan.", "energy conservation", "conserve（保护）+ -ation（行为）",
            "A coastal city adopted a conservation plan. It expanded public transport and reduced waste, yet officials also measured the cost of each change. The plan will be revised when new evidence becomes available.",
            "How will the city decide whether to revise the plan?", "It will use new evidence.", "It will ignore costs.", "It will stop public transport.",
            "The city needs a conservation plan.", "用英语提出一项减少城市浪费的措施。", "Cities can reduce waste by making recycling easier for residents."),
        lesson(4, "经济与消费",
            "economy economic market demand supply consumer consumption income revenue profit budget finance invest investment inflation recession growth productivity trade export import domestic global price value debt credit tax subsidy allocate barrier compensate",
            "demand", "需求", "Demand for the service increased steadily.", "consumer demand", "de-（完全）+ mand（命令）：强烈要求",
            "A local firm noticed rising demand for repair services. Instead of opening many stores at once, it tested one small branch and measured customer satisfaction. This approach reduced financial risk.",
            "Why did the firm begin with one branch?", "To reduce financial risk.", "To avoid customers.", "To increase prices immediately.",
            "Demand for the service increased steadily.", "用英语解释企业为什么要先做小规模试点。", "A small pilot helps a business test demand before investing more money."),
        lesson(5, "技术与社会",
            "technology digital innovation device network platform algorithm automate automation artificial intelligence privacy security database access online virtual software hardware interface update feature application efficient accurate ethical transparent architecture assemble complex construct",
            "privacy", "隐私", "The app should protect user privacy.", "privacy protection", "private（私人的）+ -acy（状态）",
            "A health app offered useful reminders but collected more data than it needed. After users raised privacy concerns, the developer removed optional tracking. The revised design kept the core service while giving users more control.",
            "What changed after users raised concerns?", "Optional tracking was removed.", "All reminders were removed.", "The app stopped working offline.",
            "The app should protect user privacy.", "用英语说明应用如何减少不必要的数据收集。", "An app should collect only the data needed for its main service."),
        lesson(6, "公共健康",
            "health public medical medicine clinic hospital patient physician disease symptom diagnosis treatment prevent prevention vaccine nutrition exercise mental physical stress risk benefit recovery infection immune hygiene policy community access inequality clarify coherent",
            "prevent", "预防；阻止", "Early action can prevent wider infection.", "prevent disease", "pre-（在前）+ vent（来）：先行阻止",
            "A community clinic offered free health information before the winter season. Staff explained simple ways to prevent infection and encouraged patients to seek help when symptoms persisted. Attendance increased after the clinic extended its hours.",
            "Why did attendance increase?", "The clinic extended its hours.", "Treatment became more expensive.", "The clinic stopped giving information.",
            "Early action can prevent wider infection.", "用英语说明公共健康信息为什么应该清楚易懂。", "Clear information helps people make safer choices before problems grow."),
        lesson(7, "社会保障",
            "society social welfare equality equity poverty household family child elderly pension insurance benefit support aid charity volunteer nonprofit vulnerable disability inclusion opportunity mobility justice distribution reform institution citizen responsibility contribute conventional",
            "welfare", "福利；福祉", "The program aims to improve child welfare.", "social welfare", "well（好）+ fare（生活状态）",
            "A town reviewed its welfare services for older residents. The survey showed that transport was a bigger problem than the number of clinics. Officials therefore added a bus route before building new facilities.",
            "What was the main problem identified by the survey?", "Transport to services.", "Too many clinics.", "A lack of surveys.",
            "The program aims to improve child welfare.", "用英语说明为什么公共服务应先了解居民需求。", "Public services work better when planners understand residents' actual needs."),
        lesson(8, "媒体与信息",
            "media news journalism journalist source publish publication audience message communication channel broadcast report headline claim fact rumor misinformation verify verification credibility editorial opinion perspective narrative influence attention exposure advertisement content share ambiguous apparent",
            "verify", "核实；验证", "Readers should verify a surprising claim.", "verify a source", "ver（真实）+ -ify（使成为）",
            "An article spread quickly because its headline sounded dramatic. A journalist checked the original source and found that the numbers were taken out of context. The correction reached fewer readers than the first post.",
            "What did the journalist discover?", "The numbers lacked context.", "The headline was too short.", "The source had no numbers.",
            "Readers should verify a surprising claim.", "用英语说明转发新闻前应检查什么。", "Before sharing news, readers should check the source and its context."),
        lesson(9, "城市与交通",
            "urban rural city town transport transportation transit commute traffic congestion infrastructure housing resident neighborhood district pedestrian bicycle vehicle route road rail subway construction density population migration expansion planning public space aspect approach",
            "traffic", "交通；车流", "Better transit may reduce road traffic.", "heavy traffic", "常见搭配：traffic congestion（交通拥堵）",
            "A city added a bus lane to reduce road traffic. Some drivers expected longer trips, but the first survey found that average travel times fell. Officials will keep measuring the effect during busy periods.",
            "What did the first survey find?", "Average travel times fell.", "Every driver stopped using the road.", "The bus lane was removed.",
            "Better transit may reduce road traffic.", "用英语提出一项改善通勤的措施。", "A reliable bus network can make commuting easier and reduce congestion."),
        lesson(10, "工作与职业",
            "work labor labour employment employee employer career occupation profession recruit recruitment salary wage workplace colleague manager leadership teamwork collaboration cooperate remote flexible schedule training qualification experience responsibility task deadline productivity balance satisfaction promotion retirement consist consistent",
            "cooperate", "合作", "Colleagues cooperate to finish on time.", "cooperate with colleagues", "co-（共同）+ operate（工作）",
            "A small company allowed staff to work from different locations. Managers set clear goals and held brief weekly meetings so teams could cooperate. Employees reported fewer interruptions, although new staff needed more guidance.",
            "What challenge remained for new staff?", "They needed more guidance.", "They had no goals.", "They could not attend meetings.",
            "Colleagues cooperate to finish on time.", "用英语说明远程团队如何保持合作。", "Remote teams can cooperate through clear goals and regular communication."),
        lesson(11, "规则与伦理",
            "law legal rule regulation standard right duty ethics ethical moral consent permission protect protection fairness fair discrimination accountability authority government democracy policy decision consequence principle conflict resolve enforce comply violation penalty arbitrary",
            "consent", "同意；许可", "Researchers asked for informed consent.", "informed consent", "con-（一起）+ sent（感受）：共同认可",
            "A research project invited volunteers to share personal information. The team explained how the data would be used and asked for informed consent. Participants could leave the project at any time without penalty.",
            "What could participants do without penalty?", "Leave the project.", "Change the research results.", "Publish other people's data.",
            "Researchers asked for informed consent.", "用英语说明知情同意为什么重要。", "Informed consent lets people decide how their information is used."),
        lesson(12, "文化与历史",
            "culture cultural history historical tradition heritage museum archive document record memory identity language literature art artist music theatre theater belief custom ritual generation ancient modern contemporary preserve preservation exhibit diverse approximate",
            "heritage", "遗产；传承", "The museum preserves local heritage.", "cultural heritage", "herit（继承）+ -age（集合或状态）",
            "A local museum collected stories from several generations of residents. Its new exhibit showed how migration changed food, language, and work in the town. Visitors could hear the stories and compare them with written records.",
            "What did the exhibit show?", "How migration changed local life.", "Why the museum closed.", "How to build a new town.",
            "The museum preserves local heritage.", "用英语说明博物馆为何要保存口述历史。", "Oral histories preserve personal experiences that official records may miss.")
    )

    val pilotWords: List<String> = postgraduate.flatMap { it.words }.distinct()
    val postgraduateForty: List<CourseLesson> = postgraduate + ExtendedCourseCatalog.postgraduate
    val ielts: List<CourseLesson> = ExtendedCourseCatalog.ielts
    val toefl: List<CourseLesson> = ExtendedCourseCatalog.toefl
    val all: List<CourseLesson> = postgraduateForty + ielts + toefl
}
