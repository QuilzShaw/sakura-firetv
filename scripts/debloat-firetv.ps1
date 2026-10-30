<#
.SYNOPSIS
  Turns off ads, tracking and telemetry on an Amazon Fire TV over ADB (no root needed).

.EXAMPLE
  .\debloat-firetv.ps1 -TvIp 192.168.1.50
  .\debloat-firetv.ps1 -TvIp 192.168.1.50 -RemoveApps   # also uninstalls the preinstalled apps listed below

.NOTES
  Before running: on the TV, enable Settings > My Fire TV > Developer Options > ADB Debugging,
  and accept the "Allow debugging?" prompt the first time. Everything here is undone by restore-firetv.ps1.
#>
param(
    [Parameter(Mandatory)] [string]$TvIp,
    [string]$Adb = "adb",
    [switch]$RemoveApps
)

$target = "${TvIp}:5555"
& $Adb connect $target | Out-Null
function Tv([string]$cmd) { & $Adb -s $target shell $cmd 2>&1 }

Write-Host "Privacy: limit ad tracking, stop usage and marketing data uploads" -ForegroundColor Magenta
foreach ($k in 'limit_ad_tracking', 'amz_limit_ad_tracking', 'non_amz_limit_ad_tracking') { Tv "settings put secure $k 1" }
foreach ($k in 'USAGE_METRICS_UPLOAD_ENABLED', 'usage_metrics_marketing_enabled', 'pact_usage_collection_enabled') { Tv "settings put secure $k 0" }

Write-Host "Suspending Amazon's protected ad, metrics and background apps (they can't be disabled)" -ForegroundColor Magenta
# Appstore: suspended so it stops running in the background; unsuspend it to update apps
foreach ($p in 'com.amazon.ftvads.deeplinking', 'com.amazon.client.metrics', 'com.amazon.venezia', 'com.amazon.diode', 'com.amazon.adep') {
    Tv "pm suspend --user 0 $p"   # adep refuses; the background block below still applies
    Tv "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore"
}

Write-Host "Power: never sleep on its own, and ignore standby requests from HDMI devices" -ForegroundColor Magenta
# Fire OS counts only remote presses as activity, so it powers off mid-session on a PC or console
Tv "settings put secure sleep_timeout 2147460000"
Tv "settings put system screen_off_timeout 2147460000"
Tv "settings put global stay_on_while_plugged_in 7"
Tv "settings put secure block_cec_standby 1"

Write-Host "Speed: animations 1x -> 0.5x" -ForegroundColor Magenta
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') { Tv "settings put global $k 0.5" }

# Disabled (not uninstalled), so `pm enable` or restore-firetv.ps1 brings them back
$disable = @(
    'com.amazon.tv.acr'                     # Automatic Content Recognition: identifies what's on screen (even HDMI) for ad targeting
    'com.amazon.hybridadidservice'          # advertising ID service
    'com.amazon.dp.logger'                  # device logging
    'com.amazon.wirelessmetrics.service'    # Wi-Fi metrics upload
    'com.amazon.device.telemetry.emitter'   # telemetry
    'com.amazon.perfcollection'             # performance data collection
    'com.amazon.tv.democontent.provider'    # store demo content
    'com.amazon.storm.lightning.tutorial'   # tutorials
    'com.amazon.tmm.tutorial'
    'com.amazon.shoptv.client'              # shopping
    'com.amazon.minitv.android.app'         # Amazon miniTV
    'com.amazon.tv.releasenotes'
    'com.amazon.gamehub'                    # Luna hub
    'com.amazon.smarthomemapviewapp'        # Alexa smart-home map
    'com.amazon.tv.forcedotaupdater.v2'     # forced update prompts
    'com.amazon.tv.easyupgrade'             # upgrade nags
    'com.amazon.kso.blackbird'              # "Special Offers" ad delivery
    'com.amazon.media.recommendations'      # sponsored recommendations
    'com.amazon.ftv.screensaver'            # screensaver that shows ads
    'com.amazon.aca'                        # promotional notifications
    'com.amazon.bueller.photos'             # Amazon Photos promo
    # Background services that kept restarting and crowding the TV's 1.8 GB of memory
    'com.amazon.ceviche'
    'com.amazon.naatyam'
    'com.amazon.device.lowstoragemanager'
    'com.amazon.whisperjoin.middleware.np'     # setup helper for other Amazon devices
    'com.amazon.whisperjoin.middleware.v2.np'
)
Write-Host "Disabling ad, telemetry and nag apps" -ForegroundColor Magenta
foreach ($p in $disable) {
    $r = Tv "pm disable-user --user 0 $p"
    if ("$r" -match 'protected') { Write-Host "  skipped $p (Amazon-protected)" -ForegroundColor DarkGray }
    elseif ("$r" -match 'disabled') { Write-Host "  disabled $p" }
}

if ($RemoveApps) {
    # Edit this list: anything you do watch, delete from it. Reinstall later from the Appstore.
    $remove = @(
        'tv.twitch.android.viewer', 'com.spotify.tv.android', 'com.xite.amazon.coda', 'com.espn.gtv',
        'com.angel.tv', 'com.crunchyroll.crunchyroid', 'tv.pluto.android', 'com.tubitv.ott',
        'com.peacock.peacockfiretv', 'com.disney.disneyplus', 'com.amazon.bueller.music'
    )
    Write-Host "Removing preinstalled apps" -ForegroundColor Magenta
    foreach ($p in $remove) { Write-Host "  $p -> $(Tv "pm uninstall --user 0 $p")" }
}

Write-Host "`nDone. Restart the TV (hold Select + Play/Pause) so every change applies." -ForegroundColor Green
Write-Host "Also on the TV: Settings > Preferences > Featured Content > turn off video and audio autoplay."
