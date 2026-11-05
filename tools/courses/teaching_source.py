"""Fixed teaching notes and contextual exercises, authored for teaWords.

VOA supplies the referenced dialogue/audio. New situations and examples below are teaWords exercises.
Each point: expression | meaning | use | common confusion | new example | source locator.
Each exercise: type | prompt | accepted answers | choices | feedback | locator | optional hint.
"""
GUIDES = [
    dict(situation='你刚搬到新住处，第一次见到邻居。你想确认对方的名字，再介绍自己。',
         mission='能用英语完成一次简短的初次见面。', listenFor='先听人物怎样确认姓名、怎样回应问候。第一遍不必记住地址和拼写。',
         points='''I am … / I’m …|我是……|主动说自己的名字。|I am 用来介绍自己；Are you 用来确认对方，顺序不同。|Hi, I’m Lina.|I am Pete.
Are you …?|你是……吗？|你大概知道对方的名字，想核实一下。|不要写成 You are ...? 来练习本课的标准问句；把 are 提到 you 前面。|Are you Ben?|Are you Anna?
Nice to meet you.|很高兴认识你。|初次见面时，回应或表达友好。|初次认识用 meet；再次见面通常说 Nice to see you。|I’m Lina. Nice to meet you.|Nice to meet you.''',
         speakingTask='想象新邻居问你“Are you Mei?”。你就是 Mei：先确认，再说很高兴认识对方。合上示范，用自己的声音说两句。',
         speakingExample='Yes, I am. Nice to meet you.', takeaway='确认对方 → 介绍自己 → 友好回应。名字换了，句子的基本结构不变。'),
    dict(situation='朋友把你介绍给另一位邻居。对方想知道你从哪里来、和谁一起住。',
         mission='能介绍自己的来源地和一位室友。', listenFor='听出 Anna 来自什么地方，以及 Marsha 和她的关系。公寓编号先不用背。',
         points='''Where are you from?|你来自哪里？|认识新朋友时，询问对方来自的地方。|问来源用 from；Where are you? 问的是现在的位置。|Where are you from, Ben?|Where are you from?
I am from …|我来自……|用地点回答来源问题。|from 后面接地点，不加额外的 am。|I’m from Beijing.|small town
… is my roommate.|……是我的室友。|介绍与你同住的人。|my 后直接接名词；roommate 与 friend 不是同一个意思。|Lina is my roommate.|roommate''',
         speakingTask='你来自 Beijing，室友叫 Lina。向新邻居介绍这两件事，每件事说一句。',
         speakingExample='I’m from Beijing. Lina is my roommate.', takeaway='Where … from 问来源；I’m from … 回答；my roommate 说明关系。'),
    dict(situation='你想做晚饭，需要打电话问室友附近有没有超市。第一次却拨错了号码。',
         mission='能在电话里确认身份，并说明自己想做什么。', listenFor='先听第一次电话出了什么问题，再听 Anna 联系到 Marsha 后提出的需求。',
         points='''This is …|我是……（电话用语）|接通电话后，向对方说明身份。|电话里常说 This is Anna；不是在指旁边的另一个人。|Hello. This is Ben.|This is Marsha.
I want to …|我想……|说明自己想做的一件事。|want 后面用 to + 动词原形；不是 want cooking。|I want to buy food.|I want to cook dinner.
Is there a … near here?|这附近有……吗？|寻找附近某种设施。|a 后面接单数名词；问有没有与问具体地址是两件事。|Is there a bank near here?|supermarket near here''',
         speakingTask='模拟给室友打电话：说自己叫 Ben，并说明你想买食物。可使用 buy food。',
         speakingExample='Hello. This is Ben. I want to buy food.', takeaway='先确认电话身份，再说明需求；想做某事用 want to + 动词。'),
    dict(situation='朋友需要一支笔，你在包里找，却接连拿出了别的东西。',
         mission='能说明自己有什么，并纠正对物品的判断。', listenFor='听清 have 和 is 分别在表达什么；不必把安娜包里的每件物品都背下来。',
         points='''I have a …|我有一个……|说明你拥有某件物品。|have 表示拥有，is 用于说明“它是什么”。|I have a map.|I have a pen in my bag.
It is a …|它是一个……|辨认眼前的单个物品。|单数可数名词前通常需要 a；不要只说 It is book。|It is a lamp.|It is a book.
It is not a …|它不是一个……|否定刚才的物品判断。|not 放在 is 后面；不是 It not is ...。|It is not a toy.|It is not a pen.''',
         speakingTask='你拿着一张地图。先告诉朋友你有一张地图，再说明它不是一本书。',
         speakingExample='I have a map. It is not a book.', takeaway='have 说拥有；is 说身份；is not 纠正判断。'),
    dict(situation='你和朋友参观房子，彼此在不同的房间，需要说清自己在哪里。',
         mission='能把房间和日常活动连成一句话。', listenFor='把每个房间和对应活动联系起来听：cook、relax、sleep。',
         points='''Where are you?|你在哪里？|寻找对方当前所在的地方。|这不是 Where are you from?；后者问来源地。|Ben, where are you?|Where are you?
I am in the …|我在……里面。|说明你所在的房间。|in 表示在里面；bedroom、kitchen 等房间前在这里用 the。|I am in the kitchen.|I am in the bathroom!
I … in the …|我在……做……|描述在某个房间的日常活动。|I am in ... 只说位置；I sleep in ... 还说明做什么。|I read in the bedroom.|I sleep in the bedroom!''',
         speakingTask='你在客厅读书。用 read 和 living room 说一句；再用 Where are you? 问朋友的位置。',
         speakingExample='I read in the living room. Where are you?', takeaway='先说明位置，再加上活动；不用把 am 放在 read、sleep 等动作前。'),
    dict(situation='你第一次去健身房，需要根据大楼里的休息室、收发室和大堂找位置。',
         mission='能用三个位置短语说明一个地点。', listenFor='重点听 across from、next to、behind。它们不是同一种位置关系。',
         points='''Where is the …?|……在哪里？|询问一个具体设施的位置。|单数地点用 is；you 则用 are。|Where is the bank?|Where is the gym?
across from / next to|在对面／紧挨着|利用另一个地点作为参照物。|across from 隔在对面；next to 紧挨着，不能互换。|The bank is next to the store.|across from the lounge
behind|在……后面|说明一个地点在另一个地点后方。|behind 不是 in；后面和里面不同。|The gym is behind the store.|behind the lobby''',
         speakingTask='给朋友指路：银行紧挨着商店，健身房在商店后面。用 bank、store、gym 各说一句。',
         speakingExample='The bank is next to the store. The gym is behind the store.', takeaway='把目标地点和参照物说清楚；位置短语选错，会把人带到不同地方。'),
    dict(situation='你第一天上班，要认识同事，也要看出他们正在忙什么。',
         mission='能询问和说明此刻正在进行的活动。', listenFor='听同事对 What are you doing? 的回答，留意 am 和动作的 -ing 形式。',
         points='''What are you doing?|你正在做什么？|询问对方此刻的活动。|不是问长期职业；此处关注“现在”。|What are you doing, Ben?|What are you doing?
I am …-ing.|我正在……|说明正在进行的动作。|不能漏掉 am；I writing 不完整。|I am reading a book.|I am doing my show!
write → writing|写 → 正在写|把 write 放进现在进行时。|write 去掉末尾不发音的 e，再加 ing；不是 writeing。|Lina is writing.|writing.''',
         speakingTask='朋友问你正在做什么。你在读一本书：用 read a book 回答，再反问对方正在做什么。',
         speakingExample='I am reading a book. What are you doing?', takeaway='正在做：be + 动词 -ing。先看主语，再选 am、is 或 are。'),
    dict(situation='昨天你打断了同事的工作。今天你想道歉，但要先确认他们是否有空。',
         mission='能礼貌询问、道歉，并回应别人的道歉。', listenFor='听 Anna 怎样开口、同事怎样回应，以及何时再见面。',
         points='''Are you busy?|你忙吗？|开口谈事之前，先确认对方是否方便。|busy 是形容词，直接和 are 搭配，不加 doing。|Are you busy, Ben?|Are you busy?
Sorry about …|为……感到抱歉|对一件具体的事情表示歉意。|sorry about yesterday 指昨天的事；不是问昨天忙不忙。|Sorry about the noise.|Sorry about yesterday.
No worries.|没关系。|表示接受道歉，告诉对方不用担心。|这是对道歉的回应；不表示“我现在不忙”。|Sorry about the noise. — No worries.|No worries.''',
         speakingTask='你打扰了 Ben，先问他忙不忙，再为噪音（the noise）道歉。',
         speakingExample='Are you busy, Ben? Sorry about the noise.', takeaway='先尊重对方的时间，再说明歉意；听懂回应的用途。'),
    dict(situation='出门前你查天气，手机却给出了另一个城市的预报。',
         mission='能询问天气，并作肯定或否定回答。', listenFor='先听天气描述，再核实预报说的是哪个地方。温度还要留意单位。',
         points='''Is it … today?|今天……吗？|询问晴朗、有风等天气状况。|it 在这里指天气；形容词放在 it 后面。|Is it cold today?|Is it sunny today?
It is / It is not …|天气……／天气不……|根据实际情况回答天气问题。|not 放在 is 后；不能因为句子短就省略 it 或 is。|It is warm today.|it is not windy today
windy / snowy / sunny|有风的／下雪的／晴朗的|描述不同天气特征。|这些是形容词；本课句型中不写 It is wind。|It is windy today.|cold and windy''',
         speakingTask='朋友问今天冷不冷。你观察到天气暖和，而且没有雪：用 warm、snowy 回答两句。',
         speakingExample='It is warm today. It is not snowy today.', takeaway='天气句用 it 作主语；单位和地点也会影响你对预报的理解。'),
    dict(situation='朋友要来你家，你得把地铁站到公寓的路线说明白。',
         mission='能按顺序给出简单的步行指引。', listenFor='听每一步的先后顺序；朋友问 Which coffee shop? 是因为地标不够明确。',
         points='''turn right / turn left|右转／左转|说明行走方向。|right 和 left 对调会改变路线；不要只记 turn。|Turn left at the bank.|turn right
Then …|然后……|连接下一步指示。|then 表示顺序，不表示同时发生。|Turn left. Then walk straight ahead.|Then walk straight ahead
across from …|在……对面|用清楚的地标说明目的地。|有多家咖啡店时，单说 near a coffee shop 不够明确。|My apartment is across from the bank.|across from a big department store''',
         speakingTask='用两句给朋友指路：先左转，再一直往前走。然后说明你的公寓在银行对面。',
         speakingExample='Turn left. Then walk straight ahead. My apartment is across from the bank.', takeaway='按实际顺序说动作，再给出容易辨认的地标。'),
    dict(situation='你要在社区里还书、寄信和买东西，需要询问地点并说明需求。',
         mission='能说明要办的事，并询问附近设施。', listenFor='听 Anna 需要做什么，以及 Marsha 用什么地标回答。',
         points='''I need to …|我需要……|说明必须完成的一件事。|need to 后面接动词原形；不是 need buying。|I need to buy food.|I need to buy stamps.
Is there a … near here?|附近有……吗？|询问某种设施是否存在。|先问有没有，不等于已经知道那家店的具体位置。|Is there a store near here?|Is there a bank near here?
There is a …|这里有一个……|回答某处存在什么。|这里的 there 不是“那个人”；There is 引出一个事物。|There is a store behind you.|There is a bank behind you.''',
         speakingTask='你要买食物。先说明需求，再问附近有没有商店（store）。',
         speakingExample='I need to buy food. Is there a store near here?', takeaway='先说需要做什么，再问地点；there is 用来引出某处的事物。'),
    dict(situation='朋友看到你的家庭照片，想知道照片上的人是谁、做什么工作。',
         mission='能分别介绍亲属关系和职业。', listenFor='区分 Who is …? 和 What does … do?；前者问身份，后者问职业。',
         points='''This is my …|这是我的……|指着照片介绍一位家人。|my 后直接接亲属名词；不再加 a。|This is my uncle.|This is my mother
Who is …?|……是谁？|询问照片中某人的身份。|不要用这个问题只回答职业；先说明人与人的关系。|Who is that man?|Who is that woman
What does … do?|……做什么工作？|询问一个人的职业。|用了 does，句末 do 保持原形，不写 does。|What does your uncle do?|What does Uncle John do?''',
         speakingTask='介绍一张照片：这是你的叔叔，他是一名老师。可使用 uncle、teacher。',
         speakingExample='This is my uncle. He is a teacher.', takeaway='介绍关系和介绍职业是两件事；一个人可以同时是你的 uncle 和一名 teacher。')
]

QUESTIONS = [
'''comprehension|这段对话主要在完成什么事情？|初次见面，确认身份并表示欢迎|向邻居借东西~初次见面，确认身份并表示欢迎~询问去超市的路|Are you Anna? 先确认身份；介绍姓名和 Nice to meet you 接着完成认识新朋友的过程。|Are you Anna?|
comprehension|Pete 把 Anna 的名字拼错后，Anna 怎样回应？|先否定，再重新拼写|再说一次地址~先否定，再重新拼写~只说谢谢|No 表示前面的拼法不对，后面的 A-N-N-A 给出正确信息。|A-N-N-A|
expression|选出向别人介绍自己的句子。|I am Lina.|Are you Lina?~I am Lina.~You am Lina.|自己用 I，搭配 am。Are you ...? 是问对方，不是在介绍自己。|I am Pete.|想一想：谁是说话的人？
expression|补全确认姓名的问句：___ you Ben?|Are|Am~Is~Are|you 搭配 are；标准问句把 are 放在 you 前面。|Are you Anna?|陈述句是 You are Ben。
expression|新同学先打招呼。你叫 Mei：用 I am 或 I’m 介绍自己，只写介绍姓名的一句。|I am Mei.~I'm Mei.||把名字换成 Mei，I am 的结构不变。Are you Mei? 是在询问别人。|I am Pete.|主语用 I；后面接 am 和你的名字。
expression|门口的人可能是 Ben。请用 Are you 开头核实他的名字，写一个完整问句。|Are you Ben?||这是确认对方身份，所以用 Are you Ben?，不能写成 I am Ben。|Are you Anna?|用 Are you + 名字。
dictation|只听原声，写出 Pete 介绍自己的完整一句。|I am Pete.||人名 Pete 放在 I am 后面，形成完整的自我介绍。|I am Pete.|
dictation|只听原声，写出 Anna 第一次说的见面问候。|Nice to meet you.||固定表达中保留 to；这句话适合初次认识时使用。|Nice to meet you.|''',
'''comprehension|Jonathan 问 Where are you from?，想了解哪件事？|Anna 来自哪里|Anna 现在站在哪里~Anna 来自哪里~Anna 的房间有多大|from 指向来源地；它和 Where are you? 的用途不同。|Where are you from?|
comprehension|Anna 说 Marsha is my roommate，说明了什么？|她们住在一起|她们只是在同一公司~她们住在一起~Marsha 刚搬到同一座城|roommate 是同住的室友，这里不仅是在说“朋友”。|roommate|
expression|补全来源句：I am ___ Beijing.|from|in~from~to|from 说明来自哪里，in 更常说明现在的位置。|small town|本题问来源，不问当前位置。
expression|选出“Lina 是我的室友”。|Lina is my roommate.|Lina am my roommate.~Lina is my roommate.~Lina is I roommate.|一个人的名字搭配 is，“我的”用 my。|roommate|“我的”不是 I。
expression|你来自 Shanghai。用 I am from 或 I’m from，向新朋友说明这件事。|I am from Shanghai.~I'm from Shanghai.||来源句是 I am from + 地点。换了城市，from 仍要保留。|small town|from 后直接接 Shanghai。
expression|你的室友叫 Ben。用 Ben 开头，写一句话介绍他与你的关系。|Ben is my roommate.||Ben 是第三人称单数，用 is；my roommate 说明关系。|roommate|Ben + is + my roommate。
dictation|听原声，写出询问来源地的问句。|Where are you from?||听清句末的 from，不能把问来源变成问位置。|Where are you from?|
dictation|听原声，写出 Anna 回答来自哪里的完整一句。|I am from a small town.||a small town 是一个小镇；a 与 from 都是句子的一部分。|I am from a small town.|''',
'''comprehension|第一次电话里，Anna 为什么没有问到超市的位置？|她拨错了号码|对方不知道超市~她拨错了号码~她忘了自己的名字|对方说 You have the wrong number。先确认联系到谁，才能继续问事。|wrong number|
comprehension|联系到 Marsha 后，Anna 的主要需求是什么？|找超市，好准备晚饭|找超市，好准备晚饭~找一个新室友~打听 Marsha 的职业|cook dinner 和 find a supermarket 连在一起，说明她要为做饭找超市。|I want to find a supermarket.|
expression|电话接通，你叫 Ben。选择合适的自我介绍。|Hello. This is Ben.|Hello. Are you Ben?~Hello. This is Ben.~Hello. This Ben.|This is + 名字是在电话中说明自己是谁。|This is Marsha.|本题要介绍自己，不是核实别人。
expression|补全：I want ___ buy food.|to|to~am~buy|want 后接 to + 动词原形；buy 已经是动词原形。|I want to cook dinner.|表示“想做某事”。
expression|你叫 Lina。模拟电话刚接通时的自我介绍，用 This is 开头。|This is Lina.||电话里可以用 This is Lina 说明身份，不需要把自己说成第三人称。|This is Marsha.|This is + 你的名字。
expression|你想买食物。用 I want to 开头，使用 buy food，写完整一句。|I want to buy food.||保留 to，后面使用 buy 的原形。|I want to cook dinner.|把 cook dinner 换成 buy food。
dictation|听原声，写出 Anna 要找超市的整句。|I want to find a supermarket.||to find 是想做的动作，a supermarket 是要找的对象。|I want to find a supermarket.|
dictation|听原声，写出 Marsha 接通电话时的两句。|Hello. This is Marsha.||This is + 名字是在电话里说明自己是谁。|Hello. This is Marsha.|''',
'''comprehension|Anna 说 I have a pen，主要在表达什么？|她有一支笔|眼前的东西一定是笔~她有一支笔~她正在写字|have 表示拥有；辨认眼前物品要用 It is ...。|I have a pen in my bag.|
comprehension|It is not a pen. It is a book. 两句连用在做什么？|纠正对物品的判断|说明书是谁的~纠正对物品的判断~问朋友有没有笔|先否定“是笔”，再给出正确判断“是书”。|It is not a pen. It is a book.|
expression|选择“我有一本书”。|I have a book.|I am a book.~It is a book.~I have a book.|I have 说明你拥有物品；I am a book 会把你说成一本书。|I have a pen in my bag.|先分清“我有”和“我是”。
expression|选出“它不是玩具”的标准句子。|It is not a toy.|It not is a toy.~It is not a toy.~It is no toy.|本课在 is 后面加 not，构成否定。|It is not a pen.|not 放在哪里？
expression|你包里有一张地图。用 I have 开头，使用 a map，说“我有一张地图”。|I have a map.||对象换成 map，拥有关系仍用 have。|I have a pen in my bag.|map 前保留 a。
expression|你拿着的是灯，不是书。只写否定“它是一本书”的句子，使用 book。|It is not a book.~It isn't a book.||把 not 放在 is 后面；也接受标准缩写 isn't。|It is not a pen.|It is not + a book。
dictation|听原声，写出 Anna 说明笔在包里的两句。|Yes. I have a pen in my bag.||in my bag 给出笔的位置；拥有关系由 I have 表达。|Yes. I have a pen in my bag.|
dictation|听原声，写出 Marsha 指出那是地图的一句。|Anna, it is a map.||称呼后用 it is a map 辨认物品。|Anna, it is a map.|''',
'''comprehension|对方问 Where are you?，你应当优先回答什么？|自己现在的位置|自己来自的城市~自己现在的位置~朋友的姓名|where 在这个问句中问当前位置；没有 from。|Where are you?|
comprehension|We relax in the living room. 把哪两件事连在一起？|休息和客厅|做饭和客厅~休息和客厅~睡觉和厨房|relax 是休息，living room 是客厅；两者组成活动与地点。|We relax in the living room.|
expression|选择“我在厨房”，只说明位置。|I am in the kitchen.|I in the kitchen.~I am in the kitchen.~I cook the kitchen.|位置句需要 am；cook 是做饭，不是位置词。|I am in the bathroom!|I 后面需要 be 动词。
expression|你想说在卧室读书。选择完整句子。|I read in the bedroom.|I am read in the bedroom.~I read in the bedroom.~I read the bedroom.|这里用一般现在时描述活动，不在 read 前加 am。|I sleep in the bedroom!|把 sleep 换成 read。
expression|用 I am in 开头，说“我在客厅”。客厅用 the living room。|I am in the living room.~I'm in the living room.||地点变化后，I am in the ... 的结构保持不变。|I am in the bathroom!|in 后接 the living room。
expression|说“我在厨房读书”，用 read、the kitchen。|I read in the kitchen.||主语 + 活动 + in + 房间，不用 I am read。|I sleep in the bedroom!|I + read + in the kitchen。
dictation|听原声，写出 Anna 说自己在厨房做什么的一句。|I eat in the kitchen.||eat 表示吃饭，地点由 in the kitchen 补充。|I eat in the kitchen.|
dictation|听原声，写出 Marsha 说在客厅做什么的一句。|We relax in the living room.||听清 we 和 relax，别把主语改成 I。|We relax in the living room.|''',
'''comprehension|across from 和 next to 可以随意互换吗？|不能，前者是对面，后者是旁边|可以，都表示在附近~不能，前者是对面，后者是旁边~可以，都表示在后面|它们给出不同位置关系，替换后路线会改变。|across from the lounge|
comprehension|Pete 补充 behind the lobby，是在帮助 Anna 明确什么？|健身房相对大堂的位置|大堂的名字~健身房相对大堂的位置~锻炼要花多长时间|behind 用大堂作为参照，补充健身房的位置。|behind the lobby|
expression|问一个具体的银行在哪里，应选哪句？|Where is the bank?|Where are the bank?~Where is the bank?~Where is you bank?|单数的 the bank 与 is 搭配。|Where is the gym?|本题的主语不是 you。
expression|银行和商店紧挨着。补全：The bank is ___ the store.|next to|across from~behind~next to|紧挨着用 next to；对面是 across from。|next to the mailroom|紧挨着，不是隔在对面。
expression|用 Where is 开头，询问商店在哪里。使用 the store。|Where is the store?||换了地点，单数问句仍用 Where is。|Where is the gym?|Where is + the store。
expression|健身房在商店后面。使用 The gym、behind、the store，写完整句子。|The gym is behind the store.||The gym 后需要 is，behind 给出相对位置。|behind the lobby|The gym + is + 位置短语。
dictation|听原声，写出只说明健身房在休息室对面的完整一句。|The gym is across from the lounge.||across from 说明目标地点在参照物的对面。|The gym is across from the lounge.|
dictation|听原声，写出 Pete 说明健身房两个方位的两句。|The gym is across from the lounge. It is behind the lobby.||across from 是对面，behind 是后面，两句提供了不同的参照物。|The gym is across from the lounge. It is behind the lobby.|lounge 是休息室，lobby 是大堂。先听清对应的方位词。''',
'''comprehension|What are you doing? 在这段对话里问什么？|对方此刻正在做的事|对方长期从事的职业~对方此刻正在做的事~对方来自的地方|这里关注正在写、读或录节目的活动，不是职业名称。|What are you doing?|
comprehension|同事们的回应说明 Anna 需要注意什么？|别人正在工作，不宜随意打断|每个人都想马上聊天~别人正在工作，不宜随意打断~大家都已经下班|对话中的 writing、recording、reading 都表明同事正在忙自己的活动。|record again|
expression|选择“我正在读一本书”。|I am reading a book.|I reading a book.~I am read a book.~I am reading a book.|现在进行时需要 am 和 reading，两者不能漏掉。|reading.|be + 动词 -ing。
expression|write 放入 I am ___ 中，应使用哪个形式？|writing|writeing~writing~write|write 去掉末尾不发音的 e 后加 ing。|writing.|write 末尾的 e 要去掉。
expression|朋友问你正在做什么。用 I am 开头，说自己正在读一本书，使用 read a book。|I am reading a book.~I'm reading a book.||此刻的活动使用 am reading；a book 是读取对象。|reading.|read 在这里变成 reading。
expression|Lina 正在写东西。用 Lina 开头，使用 write 的现在进行时，只写一句。|Lina is writing.~Lina's writing.||Lina 是第三人称单数，选 is；write 变 writing。|writing.|Lina + is + writing。
dictation|听原声，写出 Anna 单独询问对方正在做什么的整句。|What are you doing?||are 和 doing 都是这个问句的必要部分。|What are you doing?|
dictation|听原声，写出 Jonathan 说明自己正在做节目的整句。|I am doing my show!||am doing 是正在做；my show 是自己的节目。|I am doing my show!|''',
'''comprehension|Anna 要道歉前先问 Are you busy?，是在做什么？|确认现在是否方便说话|询问对方职业~确认现在是否方便说话~要求对方停止工作|这个问句让对方表达自己是否有空，再决定是否继续说事。|Are you busy?|
comprehension|Jonathan 说 No worries，主要传达什么？|接受道歉，让 Anna 别担心|表示他完全没工作~拒绝和 Anna 说话~接受道歉，让 Anna 别担心|No worries 回应歉意，不等于 I am not busy。|No worries.|
expression|同事说 Sorry about yesterday.，选择表示不介意的回应。|No worries.|Are you busy?~No worries.~Right now.|这是对道歉的回应；另外两项不能表达接受歉意。|No worries.|想想这句话的用途。
expression|补全：Are you ___?（询问对方是否忙碌）|busy|busying~busy~doing busy|busy 是形容词，可以直接和 are 搭配。|Are you busy?|这里不需要动作的 -ing。
expression|你想询问 Ben 现在忙不忙。用 Are you busy，句末加称呼 Ben。|Are you busy, Ben?||先询问状态，再用 Ben 作为称呼。|Are you busy?|Are you busy + Ben。
expression|你刚才制造了噪音。用 Sorry about 开头为此道歉，噪音用 the noise。|Sorry about the noise.||about 后接具体事情，把 yesterday 换成 the noise。|Sorry about yesterday.|Sorry about + 道歉的事情。
dictation|听原声，写出 Amelia 说自己有点忙的整句。|I'm a little busy.||a little 表示有一点忙，不能把程度改成完全不忙。|a little busy.|
dictation|听原声，写出 Jonathan 表示不介意的回答。|No worries.||worries 用复数形式；这是本课的固定回应。|No worries.|''',
'''comprehension|为什么 Anna 出门看到的天气与手机说的不同？|她看的不是华盛顿的预报|手机把冷和暖两个词读反了~她看的不是华盛顿的预报~她不会说 sunny|手机说明预报对应 Mexico City；天气词听懂了也要核实地点。|Mexico City|
comprehension|18 degrees 还不足以判断冷暖，还要确认什么？|温度单位|手机品牌~温度单位~说话人的名字|摄氏和华氏不是同一单位，原对话随后补充了 Celsius。|Celsius|
expression|补全：Is it ___ today?（今天有风吗？）|windy|wind~windy~winding|本课天气句型需要形容词 windy。|Is it windy today?|wind 的天气形容词形式是什么？
expression|选择“今天不下雪”的完整句子。|It is not snowy today.|It not snowy today.~It is not snowy today.~It is no snow today.|保留主语 it 和 is，再在 is 后加 not。|It is not snowy.|否定句也不能漏掉 is。
expression|用 Is it 开头，询问今天冷不冷，使用 cold、today。|Is it cold today?||把 sunny 换成 cold，问句结构不变。|Is it sunny today?|Is it + 天气形容词 + today。
expression|用 It is 开头，说“今天暖和”，使用 warm、today。|It is warm today.~It's warm today.||天气句用 it 作主语，warm 是形容词。|It is warm.|It is + warm + today。
dictation|听原声，写出 Anna 询问今天是否晴天的问句。|Is it sunny today?||听清 sunny，保留疑问句中的 is 和 it。|Is it sunny today?|
dictation|听原声，写出手机回答今天没有风的完整一句。|No, it is not windy today.||否定回答保留 not，它决定了句子的含义。|No, it is not windy today.|''',
'''comprehension|Anna 给出的前两次转向是什么顺序？|先右转，再左转|先右转，再左转~先左转，再右转~一直向前，不用转弯|先 Exit the Metro and turn right，再在公交站 turn left。顺序会影响路线。|Exit the Metro|
comprehension|Ashley 为什么追问 Which coffee shop?？|附近有不止一家咖啡店|她不懂 shop 的意思~附近有不止一家咖啡店~她不想去 Anna 家|There are three coffee shops 表明地标不够明确，需要补充。|Which coffee shop|
expression|要让朋友左转，应选哪个指令？|Turn left.|Turn right.~Turn left.~Walk left right.|left 是左，right 是右，不能只看 turn 就选。|turn left|先确认方向。
expression|连接下一步：Turn left. ___ walk straight ahead.|Then|Then~Where~Which|Then 引出随后的一步，where 和 which 是不同用途的疑问词。|Then walk straight ahead|这里要表达“然后”。
expression|用两个单词告诉朋友“左转”。|Turn left.||本题换成左转，要用 left 而不是照抄第一步的 right。|turn right|Turn + 左边。
expression|你的公寓在银行对面。用 My apartment 开头，使用 across from、the bank。|My apartment is across from the bank.||目标地点后加 is，再用 across from 说明与银行的位置关系。|across from a big department store|把大百货商店换成银行。
dictation|听原声，写出 Anna 说明公寓在咖啡店附近的两句。|Yes. My apartment is near a coffee shop.||near 是在附近，没有限定哪一家咖啡店。|Yes. My apartment is near a coffee shop.|
dictation|听原声，写出 Anna 改用百货商店定位公寓的整句。|Okay, my apartment is across from a big department store.||across from 给出公寓与地标的关系，比只说咖啡店附近更明确。|Okay, my apartment is across from a big department store.|department store 分写为两个词。''',
'''comprehension|Anna 说 I need to return three books，准备做什么？|归还图书|买三本书~归还图书~在图书馆工作|return 在这里是归还；need to 后面说明要办的事。|return three books|
comprehension|There is a bank behind you. 怎样帮助 Anna？|引出一个银行，并给出位置|只重复 Anna 的名字~引出一个银行，并给出位置~说明银行需要什么|There is 说明某处有一个事物，behind you 进一步定位。|There is a bank behind you.|
expression|补全：I need to ___ food.|buy|buy~buying~bought|need to 后面接原形 buy；不用 buying 或过去式。|I need to buy stamps.|to 后面需要动词原形。
expression|选择“附近有商店吗？”的完整问句。|Is there a store near here?|Is a store near here there?~Is there a store near here?~There is store?|询问有没有，使用 Is there a ... near here?。|Is there a bank near here?|从 Is there 开始组织。
expression|你需要买食物。用 I need to 开头，使用 buy food。|I need to buy food.||需求变了，need to + 动词原形的结构不变。|I need to buy stamps.|把 buy stamps 换成 buy food。
expression|你要找一家商店。用 Is there 开头问附近有没有，使用 a store、near here。|Is there a store near here?||先问存在与否；a store 是一家商店。|Is there a bank near here?|Is there + a store + near here。
dictation|听原声，写出 Anna 回答后询问附近是否有银行的两句。|No. Is there a bank near here?||Is there a ... near here? 询问附近是否存在某种设施。|No. Is there a bank near here?|
dictation|听原声，写出 Marsha 说明银行位置的整句。|There is a bank behind you.||There is 引出银行，behind you 给出位置。|There is a bank behind you.|''',
'''comprehension|Who is that woman? 和 What does Uncle John do? 有什么区别？|前者问身份，后者问职业|都只问姓名~前者问身份，后者问职业~都在问当前位置|Who 关注是谁，What does ... do 关注通常从事的职业或活动。|What does Uncle John do?|
comprehension|Anna 说 Lavender 是 my mom's sister，说明了什么？|她与 Anna 的亲属关系|她正在做的事~她与 Anna 的亲属关系~她住的房间|妈妈的姐妹是母系的姨妈；英语 aunt 本身不区分父系或母系。|my mom's sister|
expression|选择“这是我叔叔”。|This is my uncle.|This is a my uncle.~This is my uncle.~This my uncle.|my 前不再加 a；This 后需要 is。|This is my mother|my 已经限定了关系。
expression|补全：What does your uncle ___?|do|does~doing~do|前面已经用了 does，句末动词保持原形 do。|What does Uncle John do?|避免把第三人称单数变化做两次。
expression|照片里是你的叔叔。用 This is 开头，使用 my uncle 介绍关系。|This is my uncle.||my uncle 说明关系，不能省掉 is。|This is my mother|把 my mother 换成 my uncle。
expression|你的叔叔是一名老师。用 He is 开头，使用 a teacher，说出职业。|He is a teacher.~He's a teacher.||职业说明用 He is a ...；本题需要 teacher，不是重复亲属关系。|He's a chicken farmer.|把 a chicken farmer 换成 a teacher。
dictation|听原声，写出 Marsha 询问 John 职业的整句。|What does Uncle John do?||does 与 do 分别承担不同作用，句末 do 保持原形。|What does Uncle John do?|
dictation|听原声，写出 Anna 说明堂亲们做什么的整句。|They raise sheep and make sweaters.||两个动作是 raise sheep 和 make sweaters；sheep 单复数同形。|They raise sheep and make sweaters.|两个名词是 sheep（羊）和 sweaters（毛衣），注意听清两个动作。'''
]
