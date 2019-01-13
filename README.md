### 静态污点分析工具
_ _ _
- 基于WALA实现一个针对Java程序（普通Java程序，以main函数为入口点，非Android、J2EE、J2ME等）的静态污点分析工具
- 目标Java程序可能包含多个源文件、多个类（class）、多个函数实现，Source与Sink可能在不同的函数、类、文件中
- 目标程序不包含虚函数调用一类的抽象方法、接口实现
- Source和Sink均是Java库函数，即Java运行时库中的函数调用，而不是目标程序中包含的函数实现
 - Source点函数调用的返回值为污染数据
 - Sink点的参数如果被污染了，则报告问题
 - Source与Sink在外部文件SourceSink.txt中定义，其格式为
     SOURCE  API_Signature
     SINK  API_Signature
 - 每行一个SOURCE或SINK标签，然后空格，然后是WALA中函数的签名形式
- 污点传播过程与规则均自由实现，Source与Sink规则由如上定义
- 目标程序中包含少量的成员变量（field variable），数据有可能通过此类变量传播