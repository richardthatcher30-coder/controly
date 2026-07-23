; Inno Setup script for HomeControl Companion.
; Build with: "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" HomeControlCompanion.iss
; Expects the self-contained single-file publish output at
; ..\src\HomeControl.Companion\publish\HomeControl.Companion.exe — run
; publish.ps1 (or the dotnet publish command in windows-companion/README) first.

#define MyAppName "HomeControl Companion"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "HomeControl"
#define MyAppExeName "HomeControl.Companion.exe"
#define PublishDir "..\src\HomeControl.Companion\publish"

[Setup]
AppId={{B3B6C6C1-2B7B-4C7D-9E7E-6D4A6E9A6B10}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
; A tray-only utility doesn't need admin rights — installing per-user avoids
; a UAC prompt and matches how the app itself already behaves (its "Start
; with Windows" toggle writes to the current user's Run key, not machine-wide).
PrivilegesRequired=lowest
OutputDir=output
OutputBaseFilename=HomeControlCompanionSetup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "{#PublishDir}\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName} now"; Flags: nowait postinstall skipifsilent
