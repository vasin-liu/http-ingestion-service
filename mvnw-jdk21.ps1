$ErrorActionPreference = "Stop"

$jdkHome = "E:\Home\vasin.GENSOKYO\sdk\zulu-jdk21.0.9"

if (-not (Test-Path -LiteralPath $jdkHome)) {
    throw "Configured JDK 21 path does not exist: $jdkHome"
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

& "$PSScriptRoot\mvnw.cmd" "-s" "$PSScriptRoot\.mvn\settings-jdk21.xml" @Args
exit $LASTEXITCODE
