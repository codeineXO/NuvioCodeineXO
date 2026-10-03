#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef AppSourceDir
  #define AppSourceDir "..\..\..\build\compose\binaries\main-release\app\NuvioCodeineXO"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\..\build\compose\binaries\main-release\exe"
#endif
#ifndef OutputBaseFilename
  #define OutputBaseFilename "NuvioCodeineXO-Setup"
#endif
#ifndef AppIconPath
  #define AppIconPath "..\resources\icons\nuvio-app-icon-transparent.ico"
#endif

[Setup]
AppId={{5DDCEDDD-AF2C-4FF1-A980-2E08691BFCA7}
AppName=NuvioCodeineXO
AppVersion={#AppVersion}
AppVerName=NuvioCodeineXO {#AppVersion}
AppPublisher=Nuvio Media
AppPublisherURL=https://nuvio.tv
AppSupportURL=https://github.com/nuvio-app/NuvioCodeineXO
AppUpdatesURL=https://github.com/nuvio-app/NuvioCodeineXO/releases
DefaultDirName={autopf}\NuvioCodeineXO
DefaultGroupName=NuvioCodeineXO
AllowNoIcons=yes
OutputDir={#OutputDir}
OutputBaseFilename={#OutputBaseFilename}
SetupIconFile={#AppIconPath}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern dark
DisableProgramGroupPage=yes
DisableDirPage=no
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog commandline
ArchitecturesInstallIn64BitMode=x64compatible
ArchitecturesAllowed=x64compatible
MinVersion=10.0
CloseApplications=force
CloseApplicationsFilter=*.exe,*.dll
UninstallDisplayIcon={app}\NuvioCodeineXO.exe
ChangesAssociations=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppSourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\NuvioCodeineXO"; Filename: "{app}\NuvioCodeineXO.exe"
Name: "{autodesktop}\NuvioCodeineXO"; Filename: "{app}\NuvioCodeineXO.exe"; Tasks: desktopicon

[Registry]

; Deep link protocol handler: nuvio://
Root: HKA; Subkey: "Software\Classes\nuvio"; ValueType: string; ValueData: "URL:Nuvio Protocol"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\nuvio"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\nuvio\DefaultIcon"; ValueType: string; ValueData: "{app}\NuvioCodeineXO.exe,0"
Root: HKA; Subkey: "Software\Classes\nuvio\shell\open\command"; ValueType: string; ValueData: """{app}\NuvioCodeineXO.exe"" ""%1"""

; Deep link protocol handler: stremio://
Root: HKA; Subkey: "Software\Classes\stremio"; ValueType: string; ValueData: "URL:Stremio Protocol"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\stremio"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\stremio\DefaultIcon"; ValueType: string; ValueData: "{app}\NuvioCodeineXO.exe,0"
Root: HKA; Subkey: "Software\Classes\stremio\shell\open\command"; ValueType: string; ValueData: """{app}\NuvioCodeineXO.exe"" ""%1"""

; Install location tracking for future upgrades
Root: HKA; Subkey: "Software\Nuvio Media\NuvioCodeineXO-Fork"; ValueType: string; ValueName: "InstallDir"; ValueData: "{app}"; Flags: uninsdeletekey

[Run]
Filename: "{app}\NuvioCodeineXO.exe"; Description: "{cm:LaunchProgram,NuvioCodeineXO}"; Flags: nowait postinstall skipifsilent

[Code]
// Clean up legacy MSI installation if detected
procedure RemoveLegacyMsi();
var
  UninstallKey: string;
  Names: TArrayOfString;
  I: Integer;
  DisplayName: string;
  UninstallStr: string;
  ResultCode: Integer;
begin
  UninstallKey := 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall';
  if RegGetSubkeyNames(HKLM, UninstallKey, Names) then
  begin
    for I := 0 to GetArrayLength(Names) - 1 do
    begin
      if RegQueryStringValue(HKLM, UninstallKey + '\' + Names[I], 'DisplayName', DisplayName) then
      begin
        if (Pos('NuvioCodeineXO', DisplayName) > 0) and (Names[I][1] = '{') then
        begin
          if RegQueryStringValue(HKLM, UninstallKey + '\' + Names[I], 'UninstallString', UninstallStr) then
          begin
            if Pos('MsiExec', UninstallStr) > 0 then
            begin
              Exec('msiexec.exe', '/x ' + Names[I] + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
            end;
          end;
        end;
      end;
    end;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
  begin
    RemoveLegacyMsi();
  end;
end;

// Detect existing install directory from registry
procedure InitializeWizard();
var
  PrevDir: string;
begin
  if RegQueryStringValue(HKLM, 'Software\Nuvio Media\NuvioCodeineXO-Fork', 'InstallDir', PrevDir) or
     RegQueryStringValue(HKCU, 'Software\Nuvio Media\NuvioCodeineXO-Fork', 'InstallDir', PrevDir) or
     RegQueryStringValue(HKLM, 'Software\Nuvio Media\NuvioCodeineXO', 'InstallDir', PrevDir) or
     RegQueryStringValue(HKCU, 'Software\Nuvio Media\NuvioCodeineXO', 'InstallDir', PrevDir) then
  begin
    if DirExists(PrevDir) then
    begin
      WizardForm.DirEdit.Text := PrevDir;
    end;
  end;
end;

// Prompt on uninstall to delete user settings & cache
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  LocalAppDir: string;
  RoamingAppDir: string;
  HasLocalData: Boolean;
  HasRoamingData: Boolean;
begin
  if CurUninstallStep = usUninstall then
  begin
    LocalAppDir := ExpandConstant('{localappdata}\NuvioCodeineXO');
    RoamingAppDir := ExpandConstant('{userappdata}\NuvioCodeineXO');
    HasLocalData := DirExists(LocalAppDir);
    HasRoamingData := DirExists(RoamingAppDir);

    if HasLocalData or HasRoamingData then
    begin
      if MsgBox('Do you want to delete your personal application data (settings, watch history, profiles, and cache) for NuvioCodeineXO?' + #13#10#13#10 +
                '• Click ''Yes'' to permanently delete your NuvioCodeineXO personal data.' + #13#10 +
                '• Click ''No'' to preserve your settings in case you reinstall later.',
                mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
      begin
        if HasLocalData then
          DelTree(LocalAppDir, True, True, True);
        if HasRoamingData then
          DelTree(RoamingAppDir, True, True, True);
      end;
    end;
  end;
end;
