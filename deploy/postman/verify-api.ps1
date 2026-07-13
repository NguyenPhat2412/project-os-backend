param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\..\.env.local')
)

$ErrorActionPreference = 'Stop'
Import-Module Microsoft.PowerShell.Utility
$results = [System.Collections.Generic.List[object]]::new()
$createdResources = [System.Collections.Generic.List[object]]::new()
$createdProjects = [System.Collections.Generic.List[string]]::new()
$runId = [guid]::NewGuid().ToString('N')

function Read-Env([string]$Path) {
    $values = @{}
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1].Trim()] = $matches[2].Trim() }
    }
    return $values
}

function Response-Text($Response) {
    if ($null -eq $Response.Content) { return '' }
    if ($Response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Response.Content) }
    return [string]$Response.Content
}

function Invoke-Api {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Method,
        [string]$Path,
        $Body,
        [int[]]$Expected = @(200),
        [string]$Name = "$Method $Path"
    )
    $headers = @{}
    if ($Method -in @('POST', 'PUT', 'PATCH', 'DELETE')) {
        $csrf = $Session.Cookies.GetCookies([Uri]$BaseUrl)['XSRF-TOKEN']
        if ($csrf) { $headers['X-XSRF-TOKEN'] = [Uri]::UnescapeDataString($csrf.Value) }
    }
    $parameters = @{
        Uri = "$BaseUrl$Path"; Method = $Method; WebSession = $Session; Headers = $headers
        UseBasicParsing = $true; TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 20 -Compress
    }
    $status = 0
    $text = ''
    try {
        $response = Invoke-WebRequest @parameters
        $status = $response.StatusCode
        $text = Response-Text $response
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    $ok = $status -in $Expected
    $script:results.Add([pscustomobject]@{ Name = $Name; Status = $status; Pass = $ok })
    if (-not $ok) { throw "$Name returned $status; expected $($Expected -join ','). Body: $text" }
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

function New-Session { return New-Object Microsoft.PowerShell.Commands.WebRequestSession }

function Login-Admin($Env) {
    $session = New-Session
    Invoke-Api $session POST '/api/v1/auth/login' @{
        email = $Env.LOCAL_ADMIN_1_EMAIL; password = $Env.LOCAL_ADMIN_1_PASSWORD
    } @(200) 'Identity login admin' | Out-Null
    return $session
}

function Db-Snapshot([string]$ProjectId) {
    $sql = @"
select 'project:' || resource_type || ':' || count(*) from project.resource_records where project_id='$ProjectId' group by resource_type
union all select 'work:' || resource_type || ':' || count(*) from work.resource_records where project_id='$ProjectId' group by resource_type
union all select 'operations:' || resource_type || ':' || count(*) from operations.resource_records where project_id='$ProjectId' group by resource_type
union all select 'knowledge:' || resource_type || ':' || count(*) from knowledge.resource_records where project_id='$ProjectId' group by resource_type
union all select 'activity:' || resource_type || ':' || count(*) from activity.resource_records where project_id='$ProjectId' group by resource_type
order by 1;
"@
    return @($sql | docker compose exec -T postgres psql -U project_os_owner -d project_os -At)
}

function Test-Storage($Env, [string]$ProjectId) {
    Add-Type -AssemblyName System.Net.Http
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $client = [Net.Http.HttpClient]::new($handler)
    $origin = [Uri]$BaseUrl
    try {
        $loginJson = @{ email = $Env.LOCAL_ADMIN_1_EMAIL; password = $Env.LOCAL_ADMIN_1_PASSWORD } | ConvertTo-Json
        $loginBody = [Net.Http.StringContent]::new($loginJson, [Text.Encoding]::UTF8, 'application/json')
        $login = $client.PostAsync([Uri]::new($origin, '/api/v1/auth/login'), $loginBody).GetAwaiter().GetResult()
        if ([int]$login.StatusCode -ne 200) { throw "Storage login returned $([int]$login.StatusCode)" }
        $csrf = $handler.CookieContainer.GetCookies($origin)['XSRF-TOKEN']
        $client.DefaultRequestHeaders.Add('X-XSRF-TOKEN', [Uri]::UnescapeDataString($csrf.Value))
        $form = [Net.Http.MultipartFormDataContent]::new()
        $bytes = [Text.Encoding]::UTF8.GetBytes('ProjectOS Postman smoke')
        $file = [Net.Http.ByteArrayContent]::new($bytes)
        $file.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/plain')
        $form.Add($file, 'file', 'postman-smoke.txt')
        $prefix = "projects/$ProjectId/postman-smoke/$runId"
        $upload = $client.PostAsync([Uri]::new($origin, "/api/v1/storage/attachments?storagePath=$([Uri]::EscapeDataString($prefix))"), $form).GetAwaiter().GetResult()
        $script:results.Add([pscustomobject]@{ Name = 'Storage upload'; Status = [int]$upload.StatusCode; Pass = [int]$upload.StatusCode -eq 200 })
        if ([int]$upload.StatusCode -ne 200) { throw $upload.Content.ReadAsStringAsync().GetAwaiter().GetResult() }
        $stored = ($upload.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json).data.storagePath
        $encoded = [Uri]::EscapeDataString($stored)
        $download = $client.GetAsync([Uri]::new($origin, "/api/v1/storage/attachments/content?storagePath=$encoded")).GetAwaiter().GetResult()
        $script:results.Add([pscustomobject]@{ Name = 'Storage download'; Status = [int]$download.StatusCode; Pass = [int]$download.StatusCode -eq 200 })
        $delete = $client.DeleteAsync([Uri]::new($origin, "/api/v1/storage/attachments?storagePath=$encoded")).GetAwaiter().GetResult()
        $script:results.Add([pscustomobject]@{ Name = 'Storage delete'; Status = [int]$delete.StatusCode; Pass = [int]$delete.StatusCode -eq 204 })
        $missing = $client.GetAsync([Uri]::new($origin, "/api/v1/storage/attachments/content?storagePath=$encoded")).GetAwaiter().GetResult()
        $script:results.Add([pscustomobject]@{ Name = 'Storage deleted object'; Status = [int]$missing.StatusCode; Pass = [int]$missing.StatusCode -eq 404 })
        if ($results | Where-Object { $_.Name -like 'Storage*' -and -not $_.Pass }) { throw 'Storage smoke failed' }
    } finally { $client.Dispose(); $handler.Dispose() }
}

$envValues = Read-Env $EnvFile
$admin = Login-Admin $envValues
$existingProjectId = $null
$managedId = $null
$selfSession = $null
$selfPasswordCurrent = $null
$dbDuring = @()
$dbAfter = @()

try {
    Invoke-Api (New-Session) GET '/api/v1/projects' $null @(401) 'Unauthenticated projects rejected' | Out-Null
    Invoke-Api (New-Session) POST '/api/v1/auth/login' @{ email = 'missing@example.local'; password = 'wrong-password' } @(401) 'Invalid login rejected' | Out-Null
    Invoke-Api $admin GET '/api/v1/auth/me' $null @(200) 'Identity current user' | Out-Null
    Invoke-Api $admin GET '/api/v1/admin/users?size=100' $null @(200) 'Admin users list' | Out-Null
    Invoke-Api $admin GET '/api/v1/users/directory?size=100' $null @(200) 'Identity directory list' | Out-Null

    $managedPassword = 'ManagedPass123!'
    $managedEmail = "postman-managed-$runId@example.local"
    $managed = Invoke-Api $admin POST '/api/v1/admin/users' @{
        email = $managedEmail; password = $managedPassword; displayName = 'Postman Managed'; role = 'USER'
    } @(201) 'Admin user create'
    $managedId = $managed.data.id
    Invoke-Api $admin GET "/api/v1/admin/users/$managedId" $null @(200) 'Admin user detail' | Out-Null
    Invoke-Api $admin PATCH "/api/v1/admin/users/$managedId" @{ displayName = 'Postman Managed Updated' } @(200) 'Admin user patch' | Out-Null
    $managedSession = New-Session
    Invoke-Api $managedSession POST '/api/v1/auth/login' @{ email = $managedEmail; password = $managedPassword } @(200) 'Managed user login' | Out-Null

    $projects = Invoke-Api $admin GET '/api/v1/projects?size=100' $null @(200) 'Project list'
    $existingProjectId = $projects.data[0].id
    Invoke-Api $managedSession GET "/api/v1/projects/$existingProjectId" $null @(403) 'Project RBAC denied' | Out-Null
    Invoke-Api $admin DELETE "/api/v1/admin/users/$managedId" $null @(204) 'Admin user soft delete' | Out-Null
    Invoke-Api (New-Session) POST '/api/v1/auth/login' @{ email = $managedEmail; password = $managedPassword } @(401) 'Disabled user login rejected' | Out-Null

    $temporaryProject = Invoke-Api $admin POST '/api/v1/projects' @{
        name = "Postman API Smoke $runId"; description = 'temporary API verification'; status = 'active'; legacyId = "postman-$runId"
    } @(201) 'Project create'
    $temporaryProjectId = $temporaryProject.data.id
    $createdProjects.Add($temporaryProjectId)
    Invoke-Api $admin GET "/api/v1/projects/$temporaryProjectId" $null @(200) 'Project detail' | Out-Null
    Invoke-Api $admin PATCH "/api/v1/projects/$temporaryProjectId" @{ description = 'patched API verification' } @(200) 'Project patch' | Out-Null

    $groups = [ordered]@{
        project = @('members', 'roles', 'role-assignments')
        work = @('tasks', 'task-columns', 'sprints', 'epics', 'user-stories', 'bugs', 'bug-columns')
        operations = @('risks', 'budget-items', 'expenses', 'timeline-phases', 'milestones', 'meetings', 'notes', 'action-items')
        knowledge = @('folders', 'documents', 'wikis', 'wiki-links', 'attachments', 'doc-activities')
        activity = @('comments', 'notifications')
    }
    foreach ($group in $groups.GetEnumerator()) {
        $first = $true
        foreach ($resource in $group.Value) {
            $legacyId = "postman-$($resource.Replace('-', ''))-$runId"
            $created = Invoke-Api $admin POST "/api/v1/projects/$existingProjectId/$resource" @{
                legacyId = $legacyId; name = "Postman $resource"; title = "Postman $resource"; status = 'active'; position = 1
            } @(201) "$($group.Key) $resource create"
            $id = $created.data.uuid
            $createdResources.Add([pscustomobject]@{ Resource = $resource; Id = $id })
            Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/${resource}?size=200" $null @(200) "$($group.Key) $resource list" | Out-Null
            Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/$resource/$id" $null @(200) "$($group.Key) $resource detail" | Out-Null
            Invoke-Api $admin PATCH "/api/v1/projects/$existingProjectId/$resource/$id" @{ title = "Patched $resource" } @(200) "$($group.Key) $resource patch" | Out-Null
            if ($first) {
                Invoke-Api $admin PUT "/api/v1/projects/$existingProjectId/$resource/$id" @{ title = "Put $resource"; position = 2 } @(200) "$($group.Key) $resource put" | Out-Null
                $first = $false
            }
            if ($resource -in @('tasks', 'task-columns', 'bugs', 'bug-columns')) {
                $updates = [object[]]@(@{ id = $id; position = 3 })
                Invoke-Api -Session $admin -Method POST -Path "/api/v1/projects/$existingProjectId/$resource/reorder" -Body $updates -Expected @(200) -Name "$resource reorder" | Out-Null
            }
        }
    }

    $settingKey = "postman-$runId"
    Invoke-Api $admin PUT "/api/v1/projects/$existingProjectId/settings/$settingKey" @{ enabled = $true; title = 'Postman settings' } @(200) 'Project setting put' | Out-Null
    $createdResources.Add([pscustomobject]@{ Resource = 'settings'; Id = $settingKey })
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/settings/$settingKey" $null @(200) 'Project setting get' | Out-Null
    Invoke-Api $admin PATCH "/api/v1/projects/$existingProjectId/settings/$settingKey" @{ title = 'Patched settings' } @(200) 'Project setting patch' | Out-Null

    $commentTask = Invoke-Api $admin POST "/api/v1/projects/$existingProjectId/tasks" @{
        legacyId = "postman-comment-task-$runId"; title = 'Nested comment task'; status = 'active'
    } @(201) 'Nested comment parent create'
    $commentTaskId = $commentTask.data.uuid
    $createdResources.Add([pscustomobject]@{ Resource = 'tasks'; Id = $commentTaskId })
    $comment = Invoke-Api $admin POST "/api/v1/projects/$existingProjectId/tasks/$commentTaskId/comments" @{
        content = 'Postman nested comment'
    } @(201) 'Nested comment create'
    $commentId = $comment.data.uuid
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/tasks/$commentTaskId/comments" $null @(200) 'Nested comment list' | Out-Null
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/tasks/$commentTaskId/comments/$commentId" $null @(200) 'Nested comment detail' | Out-Null
    Invoke-Api $admin PATCH "/api/v1/projects/$existingProjectId/tasks/$commentTaskId/comments/$commentId" @{ content = 'Patched nested comment' } @(200) 'Nested comment patch' | Out-Null
    Invoke-Api $admin PUT "/api/v1/projects/$existingProjectId/tasks/$commentTaskId/comments/$commentId" @{ content = 'Put nested comment' } @(200) 'Nested comment put' | Out-Null
    Invoke-Api $admin DELETE "/api/v1/projects/$existingProjectId/tasks/$commentTaskId/comments/$commentId" $null @(204) 'Nested comment delete' | Out-Null

    Invoke-Api $admin POST "/api/v1/projects/$existingProjectId/activities" @{ action = 'forged' } @(405) 'Forged activity rejected' | Out-Null
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/activities?size=200" $null @(200) 'Activity list' | Out-Null
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/read-model/dashboard" $null @(200) 'Dashboard read model' | Out-Null
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/read-model/workload" $null @(200) 'Workload read model' | Out-Null
    foreach ($report in @('tasks', 'bugs', 'risks')) {
        Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/read-model/reports/$report" $null @(200) "Report read model $report" | Out-Null
    }
    Invoke-Api $admin GET "/api/v1/projects/$existingProjectId/not-a-resource" $null @(404) 'Unknown resource rejected' | Out-Null
    Test-Storage $envValues $existingProjectId

    Start-Sleep -Seconds 2
    $dbDuring = Db-Snapshot $existingProjectId

    $selfSession = New-Session
    $selfEmail = "postman-self-$runId@example.local"
    $selfPassword = 'SelfPass123!'
    $selfPassword2 = 'SelfPass456!'
    $selfPasswordCurrent = $selfPassword
    Invoke-Api $selfSession POST '/api/v1/auth/register' @{ email = $selfEmail; password = $selfPassword; displayName = 'Postman Self' } @(201) 'Identity register' | Out-Null
    Invoke-Api $selfSession GET '/api/v1/users/me/profile' $null @(200) 'Profile get' | Out-Null
    Invoke-Api $selfSession PATCH '/api/v1/users/me/profile' @{ displayName = 'Postman Self Updated'; department = 'QA'; skills = @('Postman') } @(200) 'Profile patch' | Out-Null
    Invoke-Api $selfSession GET '/api/v1/users/me/preferences/notifications' $null @(200) 'Notifications preferences get' | Out-Null
    Invoke-Api $selfSession PUT '/api/v1/users/me/preferences/notifications' @{ emailProjects = $false; channelPush = $true } @(200) 'Notifications preferences put' | Out-Null
    Invoke-Api $selfSession GET '/api/v1/users/me/preferences/appearance' $null @(200) 'Appearance get' | Out-Null
    Invoke-Api $selfSession PUT '/api/v1/users/me/preferences/appearance' @{ compactMode = $true } @(400) 'Appearance rejects unknown preference' | Out-Null
    Invoke-Api $selfSession PUT '/api/v1/users/me/preferences/appearance' @{ theme = 'dark'; mode = 'dark' } @(200) 'Appearance put' | Out-Null
    Invoke-Api $selfSession POST '/api/v1/users/me/password' @{ currentPassword = $selfPassword; newPassword = $selfPassword2 } @(204) 'Password change' | Out-Null
    $selfPasswordCurrent = $selfPassword2
    Invoke-Api $selfSession POST '/api/v1/auth/refresh' $null @(200) 'Token refresh' | Out-Null
    Invoke-Api $selfSession POST '/api/v1/auth/logout' $null @(204) 'Logout' | Out-Null
    $selfSession = New-Session
    Invoke-Api $selfSession POST '/api/v1/auth/login' @{ email = $selfEmail; password = $selfPassword2 } @(200) 'Login changed password' | Out-Null
    Invoke-Api $selfSession DELETE '/api/v1/users/me' @{ password = $selfPassword2 } @(204) 'Delete own account' | Out-Null
    $selfSession = $null
} finally {
    if ($selfSession -and $selfPasswordCurrent) {
        try { Invoke-Api $selfSession DELETE '/api/v1/users/me' @{ password = $selfPasswordCurrent } @(204, 401) 'Cleanup self account' | Out-Null } catch { Write-Warning $_ }
    }
    if ($existingProjectId) {
        for ($index = $createdResources.Count - 1; $index -ge 0; $index--) {
            $item = $createdResources[$index]
            try { Invoke-Api $admin DELETE "/api/v1/projects/$existingProjectId/$($item.Resource)/$($item.Id)" $null @(204, 404) "Cleanup $($item.Resource)" | Out-Null } catch { Write-Warning $_ }
        }
        Start-Sleep -Seconds 1
        $dbAfter = Db-Snapshot $existingProjectId
    }
    for ($index = $createdProjects.Count - 1; $index -ge 0; $index--) {
        try { Invoke-Api $admin DELETE "/api/v1/projects/$($createdProjects[$index])" $null @(204, 404) 'Cleanup project' | Out-Null } catch { Write-Warning $_ }
    }
    if ($managedId) {
        try { Invoke-Api $admin DELETE "/api/v1/admin/users/$managedId" $null @(204, 404) 'Cleanup managed user' | Out-Null } catch { Write-Warning $_ }
    }
}

$failed = @($results | Where-Object { -not $_.Pass })
[pscustomobject]@{
    RunId = $runId
    Requests = $results.Count
    Passed = @($results | Where-Object Pass).Count
    Failed = $failed.Count
    DatabaseRowsDuring = ($dbDuring -join ', ')
    DatabaseRowsAfterCleanup = ($dbAfter -join ', ')
} | Format-List

if ($failed.Count) { $failed | Format-Table -AutoSize; exit 1 }
