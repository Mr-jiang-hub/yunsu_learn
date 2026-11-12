@echo off
REM 云粟智学 后端开发启动脚本
REM 系统 JAVA_HOME 仍指向 JDK 8，Spring Boot 3 必须用 JDK 17，故在此显式指定
set "JAVA_HOME=D:\software\jdk17"
set "PATH=%JAVA_HOME%\bin;D:\software\apache-maven-3.9.16\bin;%PATH%"

REM 端口说明：文档约定 8080，但本机 8080 落在 Windows 保留端口区间（8077-8176），
REM 绑定会报 "Port 8080 was already in use"。故开发期改用 18080，
REM 前端 web/apps/*/.env.development 的 VITE_PROXY_TARGET 已同步指向 18080。
REM 若要改回 8080：把下面的 18080 换成 8080，并同步修改两个 .env.development。
set "APP_PORT=18080"

cd /d "%~dp0"
echo [1/2] JDK:
"%JAVA_HOME%\bin\java" -version
echo [2/2] 启动 dev profile，端口 %APP_PORT% ...
call mvn spring-boot:run -Dspring-boot.run.profiles=dev "-Dspring-boot.run.arguments=--server.port=%APP_PORT%"
