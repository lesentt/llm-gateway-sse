#!/usr/bin/env pwsh
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

param(
    [Parameter(Position = 0)]
    [ValidateSet("help", "up", "down", "ps", "logs", "build", "run", "test")]
    [string]$Command = "help",

    [Parameter(Position = 1)]
    [string]$Arg1,

    [Parameter(Position = 2)]
    [string]$Arg2
)

function Write-Usage {
    Write-Host "gw.ps1 - minimal project CLI"
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\tools\gw.ps1 up                 # docker compose up -d --build"
    Write-Host "  .\tools\gw.ps1 down               # docker compose down"
    Write-Host "  .\tools\gw.ps1 ps                 # docker compose ps"
    Write-Host "  .\tools\gw.ps1 logs <service>     # docker compose logs -f <service>"
    Write-Host "  .\tools\gw.ps1 build              # mvn -DskipTests package"
    Write-Host "  .\tools\gw.ps1 run                # mvn spring-boot:run"
    Write-Host "  .\tools\gw.ps1 test               # mvn test"
    Write-Host ""
}

function Compose([string[]]$Args) {
    docker compose @Args
}

switch ($Command) {
    "help" { Write-Usage; exit 0 }
    "up" { Compose @("up", "-d", "--build"); Compose @("ps"); exit 0 }
    "down" { Compose @("down"); exit 0 }
    "ps" { Compose @("ps"); exit 0 }
    "logs" {
        if (-not $Arg1) { throw "missing <service>. Example: .\tools\gw.ps1 logs app" }
        Compose @("logs", "-f", $Arg1)
        exit 0
    }
    "build" { mvn -DskipTests package; exit 0 }
    "run" { mvn spring-boot:run; exit 0 }
    "test" { mvn test; exit 0 }
}

