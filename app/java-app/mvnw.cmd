@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper Script — Windows Batch
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties

@REM Read distributionUrl from properties
for /f "tokens=2 delims==" %%i in ('findstr "distributionUrl" "%MAVEN_WRAPPER_PROPERTIES%"') do set DISTRIBUTION_URL=%%i

@REM Derive distribution file name
for %%i in (%DISTRIBUTION_URL%) do set DISTRIBUTION_FILE=%%~nxi

@REM Remove -bin.zip suffix to get distribution name for folder
set DISTRIBUTION_NAME=%DISTRIBUTION_FILE:-bin.zip=%

@REM Maven home under user profile
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\%DISTRIBUTION_NAME%\%DISTRIBUTION_NAME%
set MVNEXEC=%MAVEN_HOME%\bin\mvn.cmd

@REM Download if not present
if not exist "%MVNEXEC%" (
    echo Downloading Maven %DISTRIBUTION_NAME%...
    set TMP_FILE=%MAVEN_HOME%\..\%DISTRIBUTION_FILE%.tmp
    mkdir "%MAVEN_HOME%\.." 2>/dev/null
    powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TMP_FILE%'"
    powershell -Command "Expand-Archive -Path '%TMP_FILE%' -DestinationPath '%MAVEN_HOME%\..' -Force"
    del "%TMP_FILE%"
)

@REM Execute Maven
"%MVNEXEC%" %*
