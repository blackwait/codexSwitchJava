[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
Set-Location $projectDir

function Invoke-Tool {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ('Command failed: {0} {1}' -f $FilePath, ($Arguments -join ' '))
    }
}

function Resolve-JavaHome {
    $candidates = @(
        $env:JAVA_HOME,
        'C:\Users\Administrator\.jdks\ms-21.0.10',
        'C:\Java\jdk-21',
        'C:\Program Files\Java\jdk-21',
        'C:\Program Files\Eclipse Adoptium\jdk-21'
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if ((Test-Path (Join-Path $candidate 'bin\java.exe')) -and (Test-Path (Join-Path $candidate 'bin\jpackage.exe'))) {
            return $candidate
        }
    }

    throw 'JDK 21+ with jpackage.exe was not found.'
}

function Resolve-MavenCommand {
    if ($env:MAVEN_HOME) {
        $mavenHomeCommand = Join-Path ${env:MAVEN_HOME} 'bin\mvn.cmd'
        if (Test-Path $mavenHomeCommand) {
            return $mavenHomeCommand
        }
    }

    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $candidates = @()
    $candidates += @(
        'G:\apache-maven-3.9.9\bin\mvn.cmd',
        'F:\apache-maven-3.9.9\bin\mvn.cmd'
    )
    $candidates = $candidates | Where-Object { $_ -and (Test-Path $_) }

    if ($candidates.Count -gt 0) {
        return $candidates[0]
    }

    throw 'Maven command was not found.'
}

function Resolve-WixHome {
    $candidates = @(
        $env:WIX_HOME,
        'C:\wix314'
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if ((Test-Path (Join-Path $candidate 'candle.exe')) -and (Test-Path (Join-Path $candidate 'light.exe'))) {
            return $candidate
        }
    }

    return $null
}

$javaHome = Resolve-JavaHome
$mvnCmd = Resolve-MavenCommand
$wixHome = Resolve-WixHome
$jpackageExe = Join-Path $javaHome 'bin\jpackage.exe'
$env:JAVA_HOME = $javaHome
$env:PATH = ('{0};{1}' -f (Join-Path $javaHome 'bin'), $env:PATH)
if ($wixHome) {
    $env:PATH = ('{0};{1}' -f $wixHome, $env:PATH)
}

$iconPath = Join-Path $projectDir 'src\main\resources\assets\icon_app.ico'
$distDir = Join-Path $projectDir 'dist\win'
$targetDir = Join-Path $projectDir 'target'
$jpackageInput = Join-Path $targetDir 'jpackage-input'

Write-Host '[1/4] Maven package'
Invoke-Tool -FilePath $mvnCmd -Arguments @('-q', 'clean', 'package', '-DskipTests', '-Djavafx.platform=win')

Write-Host '[2/4] Resolve version'
$version = (& $mvnCmd '-q' 'help:evaluate' '-Dexpression=project.version' '-DforceStdout').Trim()
$appVersion = $version -replace '-SNAPSHOT$', ''
$jarName = 'codex-switcher-javafx-{0}.jar' -f $version

if (!(Test-Path (Join-Path $jpackageInput $jarName))) {
    throw ('Main jar was not found in jpackage input: {0}' -f $jarName)
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Get-ChildItem $distDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

Write-Host '[3/4] Build app-image'
Invoke-Tool -FilePath $jpackageExe -Arguments @(
    '--type', 'app-image',
    '--name', 'CodexSwitcher',
    '--app-version', $appVersion,
    '--input', $jpackageInput,
    '--main-jar', $jarName,
    '--main-class', 'com.codexswitcher.app.Launcher',
    '--dest', $distDir,
    '--icon', $iconPath,
    '--java-options', '-Dfile.encoding=UTF-8',
    '--vendor', 'CodexSwitcher'
)

if ($wixHome) {
    Write-Host '[4/4] Build Windows installer'
    Invoke-Tool -FilePath $jpackageExe -Arguments @(
        '--type', 'exe',
        '--name', 'CodexSwitcher',
        '--app-version', $appVersion,
        '--input', $jpackageInput,
        '--main-jar', $jarName,
        '--main-class', 'com.codexswitcher.app.Launcher',
        '--dest', $distDir,
        '--icon', $iconPath,
        '--win-dir-chooser',
        '--win-shortcut',
        '--win-menu',
        '--win-menu-group', 'CodexSwitcher',
        '--java-options', '-Dfile.encoding=UTF-8',
        '--vendor', 'CodexSwitcher'
    )
} else {
    Write-Host '[4/4] Skip installer because WiX is unavailable'
}

Write-Host ('Done: {0}' -f $distDir)
