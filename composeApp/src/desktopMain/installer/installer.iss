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
RestartApplications=no
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

; Launch uninstaller with /SILENT so Inno Setup built-in message boxes are suppressed
; allowing the single unified dark window in InitializeUninstallProgressForm to handle confirmation and progress
Root: HKA; Subkey: "Software\Microsoft\Windows\CurrentVersion\Uninstall\{{5DDCEDDD-AF2C-4FF1-A980-2E08691BFCA7}_is1"; ValueType: string; ValueName: "UninstallString"; ValueData: """{uninstallexe}"" /SILENT"; Flags: preservestringtype

[Run]
Filename: "{app}\NuvioCodeineXO.exe"; Description: "{cm:LaunchProgram,NuvioCodeineXO}"; Flags: nowait postinstall skipifsilent

[Code]
var
  ConfirmPage: TNewNotebookPage;
  ProgressPage: TNewNotebookPage;
  DeleteDataCheckbox: TNewCheckBox;
  UninstallBtn: TNewButton;
  ConfirmedUninstall: Boolean;

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

// Helper to force terminate running app instances before uninstalling
procedure StopRunningApp();
var
  ResultCode: Integer;
begin
  Exec('taskkill.exe', '/F /IM NuvioCodeineXO.exe /T', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

// If invoked without /SILENT (e.g. user manually clicked unins000.exe in install folder),
// re-launch with /SILENT so that the single unified window is used instead of default message boxes.
function InitializeUninstall(): Boolean;
var
  ResultCode: Integer;
  Params: string;
  I: Integer;
begin
  Result := True;
  if not UninstallSilent then
  begin
    Params := '/SILENT';
    for I := 1 to ParamCount do
    begin
      Params := Params + ' "' + ParamStr(I) + '"';
    end;
    Exec(ExpandConstant('{uninstallexe}'), Params, '', SW_SHOW, ewNoWait, ResultCode);
    Result := False;
  end;
end;

procedure UninstallBtnClick(Sender: TObject);
begin
  ConfirmedUninstall := True;
  UninstallProgressForm.Close;
end;

procedure CancelBtnClick(Sender: TObject);
begin
  ConfirmedUninstall := False;
  UninstallProgressForm.Close;
end;

// Unified single-window uninstallation: embeds confirmation & appdata options directly into UninstallProgressForm
procedure InitializeUninstallProgressForm();
var
  PromptLabel: TNewStaticText;
begin
  ProgressPage := UninstallProgressForm.InnerNotebook.ActivePage;

  ConfirmPage := TNewNotebookPage.Create(UninstallProgressForm);
  ConfirmPage.Notebook := UninstallProgressForm.InnerNotebook;

  PromptLabel := TNewStaticText.Create(ConfirmPage);
  PromptLabel.Parent := ConfirmPage;
  PromptLabel.Left := ScaleX(10);
  PromptLabel.Top := ScaleY(15);
  PromptLabel.Width := ConfirmPage.ClientWidth - ScaleX(20);
  PromptLabel.Caption := 'Are you sure you want to completely remove NuvioCodeineXO from your computer?';
  PromptLabel.WordWrap := True;

  DeleteDataCheckbox := TNewCheckBox.Create(ConfirmPage);
  DeleteDataCheckbox.Parent := ConfirmPage;
  DeleteDataCheckbox.Left := ScaleX(10);
  DeleteDataCheckbox.Top := ScaleY(55);
  DeleteDataCheckbox.Width := ConfirmPage.ClientWidth - ScaleX(20);
  DeleteDataCheckbox.Caption := 'Also delete leftover application data from your PC (%appdata% and %localappdata%)';
  DeleteDataCheckbox.Checked := False;

  UninstallBtn := TNewButton.Create(UninstallProgressForm);
  UninstallBtn.Parent := UninstallProgressForm;
  UninstallBtn.Caption := '&Uninstall';
  UninstallBtn.Left := UninstallProgressForm.CancelButton.Left - UninstallProgressForm.CancelButton.Width - ScaleX(10);
  UninstallBtn.Top := UninstallProgressForm.CancelButton.Top;
  UninstallBtn.Width := UninstallProgressForm.CancelButton.Width;
  UninstallBtn.Height := UninstallProgressForm.CancelButton.Height;
  UninstallBtn.OnClick := @UninstallBtnClick;

  UninstallProgressForm.CancelButton.OnClick := @CancelBtnClick;

  UninstallProgressForm.PageNameLabel.Caption := 'Uninstall NuvioCodeineXO';
  UninstallProgressForm.PageDescriptionLabel.Caption := 'Please confirm uninstallation before proceeding.';

  UninstallProgressForm.InnerNotebook.ActivePage := ConfirmPage;

  UninstallProgressForm.ShowModal();

  if not ConfirmedUninstall then
  begin
    Abort;
  end;

  // Make sure running instance is closed before uninstallation proceeds
  StopRunningApp();

  UninstallBtn.Visible := False;
  UninstallProgressForm.InnerNotebook.ActivePage := ProgressPage;
  UninstallProgressForm.PageNameLabel.Caption := 'Uninstalling NuvioCodeineXO';
  UninstallProgressForm.PageDescriptionLabel.Caption := 'Please wait while NuvioCodeineXO is removed from your computer.';
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  LocalAppDir: string;
  RoamingAppDir: string;
  AppDir: string;
  ResultCode: Integer;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    if Assigned(DeleteDataCheckbox) and DeleteDataCheckbox.Checked then
    begin
      LocalAppDir := ExpandConstant('{localappdata}\NuvioCodeineXO');
      RoamingAppDir := ExpandConstant('{userappdata}\NuvioCodeineXO');
      if DirExists(LocalAppDir) then
        DelTree(LocalAppDir, True, True, True);
      if DirExists(RoamingAppDir) then
        DelTree(RoamingAppDir, True, True, True);
    end;

    // Clean up entire install directory ({app}) after uninstaller exits
    // Inno Setup keeps unins000.exe running from {app} during execution, so schedule cmd rmdir once process terminates
    AppDir := ExpandConstant('{app}');
    if DirExists(AppDir) then
    begin
      Exec('cmd.exe', '/c start "" /min cmd.exe /c "timeout /t 2 /nobreak >nul & rmdir /s /q """' + AppDir + '""""', '', SW_HIDE, ewNoWait, ResultCode);
    end;
  end;
end;
