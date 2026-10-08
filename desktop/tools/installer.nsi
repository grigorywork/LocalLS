; Native NSIS installer: embeds the genuine electron-builder Windows output.
; WriteUninstaller is compiled directly; no Wine execution is needed at build time.
!include "MUI2.nsh"
!include "x64.nsh"
!include "WinVer.nsh"
Name "LocalLS"
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\Programs\LocalLS"
InstallDirRegKey HKCU "Software\LocalLS" "InstallDir"
BrandingText "LocalLS · SSH / SFTP"
ShowInstDetails show
ShowUninstDetails show
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\LocalLS.exe"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Russian"
!insertmacro MUI_LANGUAGE "English"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "LocalLS требует 64-разрядную Windows 10/11."
    Abort
  ${EndIf}
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_OK|MB_ICONSTOP "LocalLS требует Windows 10 или новее."
    Abort
  ${EndIf}
  SetRegView 64
  SetShellVarContext current
FunctionEnd

Section "LocalLS" install
  SetOutPath "$INSTDIR"
  SetOverwrite on
  File /r "${PROJECT_DIR}\artifacts\win-unpacked\*"
  WriteUninstaller "$INSTDIR\Uninstall LocalLS.exe"
  WriteRegStr HKCU "Software\LocalLS" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "DisplayName" "LocalLS"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "Publisher" "LocalLS"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "DisplayIcon" "$INSTDIR\LocalLS.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "UninstallString" '$"$INSTDIR\Uninstall LocalLS.exe$"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS" "NoRepair" 1
  CreateDirectory "$SMPROGRAMS\LocalLS"
  CreateShortcut "$SMPROGRAMS\LocalLS\LocalLS.lnk" "$INSTDIR\LocalLS.exe"
  CreateShortcut "$DESKTOP\LocalLS.lnk" "$INSTDIR\LocalLS.exe"
SectionEnd

Function un.onInit
  SetRegView 64
  SetShellVarContext current
  MessageBox MB_OKCANCEL|MB_ICONINFORMATION "Закройте LocalLS через пункт «Выйти» в системном трее, прежде чем удалять приложение. Настройки пользователя сохранятся." IDOK +2
  Abort
FunctionEnd

Section "Uninstall"
  ; Delete only files from the packaged manifest, never arbitrary user files.
  !include "${PROJECT_DIR}\tools\uninstall-files.nsh"
  Delete "$INSTDIR\Uninstall LocalLS.exe"
  RMDir "$INSTDIR"
  Delete "$DESKTOP\LocalLS.lnk"
  Delete "$SMPROGRAMS\LocalLS\LocalLS.lnk"
  RMDir "$SMPROGRAMS\LocalLS"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LocalLS"
  DeleteRegKey HKCU "Software\LocalLS"
SectionEnd
