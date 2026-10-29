<#
.SYNOPSIS
  Undoes debloat-firetv.ps1: re-enables every disabled app and restores the default settings.
  Apps removed with -RemoveApps come back with a reinstall from the Appstore.

.EXAMPLE
  .\restore-firetv.ps1 -TvIp 192.168.1.50
#>
param(
    [Parameter(Mandatory)] [string]$TvIp,
    [string]$Adb = "adb"
)

$target = "${TvIp}:5555"
& $Adb connect $target | Out-Null
function Tv([string]$cmd) { & $Adb -s $target shell $cmd 2>&1 }

$pkgs = 'com.amazon.tv.acr', 'com.amazon.hybridadidservice', 'com.amazon.dp.logger', 'com.amazon.wirelessmetrics.service',
        'com.amazon.device.telemetry.emitter', 'com.amazon.perfcollection', 'com.amazon.tv.democontent.provider',
        'com.amazon.storm.lightning.tutorial', 'com.amazon.tmm.tutorial', 'com.amazon.shoptv.client',
        'com.amazon.minitv.android.app', 'com.amazon.tv.releasenotes', 'com.amazon.gamehub',
        'com.amazon.smarthomemapviewapp', 'com.amazon.tv.forcedotaupdater.v2', 'com.amazon.tv.easyupgrade',
        'com.amazon.kso.blackbird', 'com.amazon.media.recommendations', 'com.amazon.ftv.screensaver',
        'com.amazon.vizzini', 'com.amazon.aca', 'com.amazon.bueller.photos'
foreach ($p in $pkgs) { Tv "pm enable $p" | Out-Null }
foreach ($p in 'com.amazon.ftvads.deeplinking', 'com.amazon.client.metrics') {
    Tv "pm unsuspend --user 0 $p" | Out-Null
    Tv "cmd appops set $p RUN_ANY_IN_BACKGROUND allow" | Out-Null
}

foreach ($k in 'limit_ad_tracking', 'amz_limit_ad_tracking', 'non_amz_limit_ad_tracking') { Tv "settings put secure $k 0" }
foreach ($k in 'USAGE_METRICS_UPLOAD_ENABLED', 'usage_metrics_marketing_enabled', 'pact_usage_collection_enabled') { Tv "settings put secure $k 1" }
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') { Tv "settings put global $k 1.0" }
Tv "settings put secure sleep_timeout 840000"
Tv "settings put global stay_on_while_plugged_in 0"
Tv "settings put secure block_cec_standby 0"

Write-Host "Restored. Restart the TV to finish." -ForegroundColor Green
