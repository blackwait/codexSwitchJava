[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
Set-Location $projectDir

function Resolve-MavenCommand {
    $candidates = @()
    if ($env:MAVEN_HOME) {
        $candidates += (Join-Path ${env:MAVEN_HOME} 'bin\mvn.cmd')
    }
    $candidates += @(
        'G:\apache-maven-3.9.9\bin\mvn.cmd',
        'F:\apache-maven-3.9.9\bin\mvn.cmd'
    )
    $candidates = $candidates | Where-Object { $_ -and (Test-Path $_) }

    if ($candidates.Count -gt 0) {
        return $candidates[0]
    }

    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    throw 'Maven command was not found.'
}

$mvnCmd = Resolve-MavenCommand
& $mvnCmd '-q' 'javafx:run' '-Djavafx.platform=win'
if ($LASTEXITCODE -ne 0) {
    throw 'JavaFX run failed.'
}
