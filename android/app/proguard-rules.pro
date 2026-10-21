# JNI entry points for the llama.cpp / whisper.cpp engines (native libs are looked up by name).
-keepclasseswithmembernames class app.bitcall.ai.LlamaBridge { native <methods>; }
-keepclasseswithmembernames class app.bitcall.ai.WhisperBridge { native <methods>; }
