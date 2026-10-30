<#
.SYNOPSIS
  Installs Sakura Home on a Fire TV and hands it the Home button.

.EXAMPLE
  .\install-sakura-home.ps1 -TvIp 192.168.1.50 -Apk .\SakuraHome-1.5.apk

.NOTES
  Fire OS won't let you change the default launcher, so Sakura Home uses an accessibility
  service to catch the Home button. Re-run this after every reinstall: Android unbinds
  accessibility services when their app is updated.
  To go back to Amazon's home screen: .\install-sakura-home.ps1 -TvIp ... -Uninstall
#>
param(
    [Parameter(Mandatory)] [string]$TvIp,
    [string]$Apk = "SakuraHome-1.5.apk",
    [string]$Adb = "adb",
    [switch]$Uninstall
)

$target = "${TvIp}:5555"
& $Adb connect $target | Out-Null
$service = "com.sakura.home/com.sakura.home.HomeButtonService"

if ($Uninstall) {
    & $Adb -s $target shell "settings delete secure enabled_accessibility_services"
    & $Adb -s $target uninstall com.sakura.home
    Write-Host "Sakura Home removed; the Home button goes to Amazon's screen again." -ForegroundColor Green
    return
}

& $Adb -s $target install -r $Apk
# Clear it first: setting the same value again doesn't rebind the updated service
& $Adb -s $target shell "settings put secure enabled_accessibility_services ''; sleep 1; settings put secure enabled_accessibility_services $service"
& $Adb -s $target shell "settings put secure accessibility_enabled 1"
& $Adb -s $target shell "am start -n com.sakura.home/.MainActivity" | Out-Null
Write-Host "Sakura Home installed and set as the Home button. Press Home on the remote to try it." -ForegroundColor Green
