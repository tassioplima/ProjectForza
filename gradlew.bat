@rem Gradle Wrapper (Windows)
@echo off
setlocal
set DIRNAME=%~dp0
set JAVA_EXE=java.exe
%JAVA_EXE% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
