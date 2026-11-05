# Gson DTOs are Kotlin data classes without generated adapters.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.teameow.teawords.data.WordResponse { *; }
-keep class com.teameow.teawords.data.Phonetic { *; }
-keep class com.teameow.teawords.data.Meaning { *; }
-keep class com.teameow.teawords.data.Definition { *; }
-keep class com.teameow.teawords.data.MyMemoryResponse { *; }
-keep class com.teameow.teawords.data.ResponseData { *; }
-keepclassmembers class com.teameow.teawords.data.pronunciation.** { <fields>; }
-keep class com.teameow.teawords.data.PendingAppUpdate { *; }
-keep class com.teameow.teawords.data.UpdateApk { *; }
-keep class com.teameow.teawords.data.pronunciation.WordPronunciationEntity { *; }

# Gson course DTOs
-keep class com.teameow.teawords.data.CoursePack { *; }
-keep class com.teameow.teawords.data.CourseLesson { *; }
-keep class com.teameow.teawords.data.CourseSource { *; }
-keep class com.teameow.teawords.data.CourseAudio { *; }
-keep class com.teameow.teawords.data.CourseSegment { *; }
-keep class com.teameow.teawords.data.CourseKeyPhrase { *; }
-keep class com.teameow.teawords.data.CourseExplanation { *; }
-keep class com.teameow.teawords.data.CourseQuestion { *; }
-keep class com.teameow.teawords.data.CourseRepository$Checkpoint { *; }
