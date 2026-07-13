[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$EnvironmentFile = "",
    [switch]$KeepData
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    $EnvironmentFile = Join-Path $PSScriptRoot "..\.env"
}

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Environment file not found: $Path"
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $name, $value = $line -split '=', 2
        $values[$name.Trim()] = $value.Trim().Trim('"').Trim("'")
    }
    return $values
}

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Assertion failed: $Message" }
}

function Get-CsrfHeader([Microsoft.PowerShell.Commands.WebRequestSession]$Session) {
    $cookie = $Session.Cookies.GetCookies([uri]$BaseUrl) |
        Where-Object Name -eq "XSRF-TOKEN" |
        Select-Object -First 1
    if ($null -eq $cookie) { throw "The authenticated session has no XSRF-TOKEN cookie" }
    return @{ "X-XSRF-TOKEN" = $cookie.Value }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [object]$Body,
        [hashtable]$Headers = @{}
    )

    $arguments = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
    }
    if ($null -ne $Session) { $arguments.WebSession = $Session }
    if ($Headers.Count -gt 0) { $arguments.Headers = $Headers }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json"
        $arguments.Body = ConvertTo-Json -InputObject $Body -Depth 20 -Compress
    }

    $arguments.UseBasicParsing = $true
    try {
        $response = Invoke-WebRequest @arguments
        $status = [int]$response.StatusCode
        $raw = $response.Content
        $responseHeaders = $response.Headers
    }
    catch [System.Net.WebException] {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) { throw }
        $status = [int]$errorResponse.StatusCode
        $responseHeaders = $errorResponse.Headers
        $reader = [System.IO.StreamReader]::new($errorResponse.GetResponseStream())
        try { $raw = $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    $json = $null
    if (-not [string]::IsNullOrWhiteSpace($raw) -and
        ($responseHeaders["Content-Type"] -match "json" -or $raw.TrimStart().StartsWith("{"))) {
        $json = $raw | ConvertFrom-Json
    }
    return [pscustomobject]@{
        Status = $status
        Json = $json
        Raw = $raw
        Headers = $responseHeaders
    }
}

function Send-MultipartFile {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [Parameter(Mandatory)][hashtable]$Headers
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = $Session.Cookies
    $client = [System.Net.Http.HttpClient]::new($handler)
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $stream = [System.IO.File]::OpenRead($FilePath)
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/plain")
    $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))
    foreach ($entry in $Headers.GetEnumerator()) { $client.DefaultRequestHeaders.Add($entry.Key, [string]$entry.Value) }
    try {
        $response = $client.PostAsync($Uri, $multipart).GetAwaiter().GetResult()
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            Headers = $response.Headers
        }
    }
    finally {
        $multipart.Dispose()
        $stream.Dispose()
        $client.Dispose()
        $handler.Dispose()
    }
}

function Expect-Status($Response, [int[]]$Expected, [string]$Step) {
    if ($Response.Status -notin $Expected) {
        throw "$Step returned HTTP $($Response.Status): $($Response.Raw)"
    }
    Write-Host ("[PASS] {0} -> HTTP {1}" -f $Step, $Response.Status)
    return $Response
}

function Login([string]$Email, [string]$Password) {
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $response = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Session $session `
        -Body @{ email = $Email; password = $Password }
    Expect-Status $response 200 "Login $Email" | Out-Null
    return $session
}

$env = Read-DotEnv $EnvironmentFile
foreach ($required in "BOOTSTRAP_ADMIN_EMAIL", "BOOTSTRAP_ADMIN_PASSWORD") {
    if ([string]::IsNullOrWhiteSpace($env[$required])) { throw "$required is required in $EnvironmentFile" }
}

$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$qaEmail = "codex.smoke.$runId@projectos.local"
$qaPassword = "Qa!$([guid]::NewGuid().ToString('N'))Aa1"
$bootstrap = Login $env.BOOTSTRAP_ADMIN_EMAIL $env.BOOTSTRAP_ADMIN_PASSWORD
$bootstrapCsrf = Get-CsrfHeader $bootstrap

$createdUser = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/admin/users" -Session $bootstrap `
    -Headers $bootstrapCsrf -Body @{
        email = $qaEmail
        password = $qaPassword
        displayName = "Codex Phase Smoke $runId"
        role = "ROOT_ADMIN"
    }) 201 "Identity admin user create"
$qaUserId = $createdUser.Json.data.id
$session = Login $qaEmail $qaPassword

$projectId = $null
$organizationId = $null
$employeeId = $null
$departmentId = $null
$assignmentId = $null
$scheduleId = $null
$shiftId = $null
$storagePath = $null
$attachmentFile = $null

try {
    # Phase 0/1: security, traceable errors, identity, profile and organization.
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/auth/me" -Session $session) 200 "Identity current user" | Out-Null
    $providers = Expect-Status (Invoke-Api -Method GET -Path "/api/v1/auth/providers" -Session $session) 200 "Identity auth providers"
    Assert-That ($null -ne $providers.Json.data.google) "Google provider availability must be explicit"

    $csrf = Get-CsrfHeader $session
    Expect-Status (Invoke-Api -Method PATCH -Path "/api/v1/users/me/profile" -Session $session -Headers $csrf `
        -Body @{ phone = "0900000000"; jobTitle = "QA Automation"; bio = "ProjectOS phase smoke" }) 200 "Identity profile update" | Out-Null
    Expect-Status (Invoke-Api -Method PUT -Path "/api/v1/users/me/preferences/notifications" -Session $session -Headers $csrf `
        -Body @{ taskAssigned = $true; bugAssigned = $true; email = $false }) 200 "Identity notification preferences" | Out-Null
    Expect-Status (Invoke-Api -Method PUT -Path "/api/v1/users/me/preferences/appearance" -Session $session -Headers $csrf `
        -Body @{ theme = "dark"; mode = "dark"; fontSize = "14" }) 200 "Identity appearance preferences" | Out-Null

    $refreshed = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/auth/refresh" -Session $session -Headers $csrf) 200 "Refresh-token rotation"
    Assert-That ($refreshed.Json.data.expiresIn -gt 0) "Refresh must issue a usable access session"
    $csrf = Get-CsrfHeader $session

    $unauthorized = Invoke-Api -Method GET -Path "/api/v1/auth/me"
    Expect-Status $unauthorized 401 "Unauthenticated request rejection" | Out-Null
    $invalidProject = Invoke-Api -Method POST -Path "/api/v1/projects" -Session $session -Headers $csrf -Body @{ name = "" }
    Expect-Status $invalidProject 400 "Validation error envelope" | Out-Null
    Assert-That (-not [string]::IsNullOrWhiteSpace($invalidProject.Json.error.traceId)) "Validation errors must include traceId"

    $organization = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations" -Session $session -Headers $csrf `
        -Body @{ name = "QA Organization $runId"; slug = "qa-$runId"; timezone = "Asia/Bangkok" }) 201 "Organization create"
    $organizationId = $organization.Json.data.id
    Expect-Status (Invoke-Api -Method PATCH -Path "/api/v1/organizations/$organizationId" -Session $session -Headers $csrf `
        -Body @{ name = "QA Organization $runId verified" }) 200 "Organization update" | Out-Null

    $department = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/departments" -Session $session -Headers $csrf `
        -Body @{ name = "Quality Engineering" }) 201 "Department create"
    $departmentId = $department.Json.data.id
    Expect-Status (Invoke-Api -Method PATCH -Path "/api/v1/organizations/$organizationId/departments/$departmentId" -Session $session -Headers $csrf `
        -Body @{ name = "Quality Engineering Verified" }) 200 "Department update" | Out-Null

    $employee = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/employees" -Session $session -Headers $csrf `
        -Body @{ fullName = "Codex QA"; email = $qaEmail; title = "QA Engineer"; departmentId = $departmentId }) 201 "Employee create"
    $employeeId = $employee.Json.data.id
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/employees/$employeeId/link-user" -Session $session -Headers $csrf `
        -Body @{ userId = $qaUserId }) 200 "Employee-user link and membership" | Out-Null

    # Phase 2: attendance MVP, including duplicate prevention and reporting.
    $shift = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/shifts" -Session $session -Headers $csrf `
        -Body @{ name = "QA Day Shift"; startTime = "08:00"; endTime = "17:00"; breakMinutes = 60 }) 201 "Attendance shift create"
    $shiftId = $shift.Json.data.id
    $isoDay = [int](Get-Date).DayOfWeek
    $isoDay = (($isoDay + 6) % 7) + 1
    $schedule = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/schedules" -Session $session -Headers $csrf `
        -Body @{ name = "QA Weekly Schedule"; slots = @(@{ shiftId = $shiftId; dayOfWeek = $isoDay }) }) 201 "Work schedule create"
    $scheduleId = $schedule.Json.data.id
    $today = (Get-Date).ToString("yyyy-MM-dd")
    $assignment = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/assignments" -Session $session -Headers $csrf `
        -Body @{ employeeId = $employeeId; scheduleId = $scheduleId; effectiveFrom = $today }) 201 "Schedule assignment create"
    $assignmentId = $assignment.Json.data.id
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/check-in" -Session $session -Headers $csrf) 200 "Attendance check-in" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/check-in" -Session $session -Headers $csrf) 409 "Duplicate check-in prevention" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/check-out" -Session $session -Headers $csrf) 200 "Attendance check-out" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/organizations/$organizationId/attendance/timesheet?from=$today&to=$today" -Session $session) 200 "Timesheet read model" | Out-Null

    $adjustment = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/adjustments" -Session $session -Headers $csrf `
        -Body @{ workDate = $today; checkInAt = (Get-Date).ToUniversalTime().AddHours(-8).ToString("o"); reason = "QA verified adjustment" }) 201 "Attendance adjustment request"
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/adjustments/$($adjustment.Json.data.id)/decision" -Session $session -Headers $csrf `
        -Body @{ decision = "approve"; note = "QA approval" }) 200 "Attendance adjustment approval" | Out-Null

    $leaveDate = (Get-Date).AddDays(14).ToString("yyyy-MM-dd")
    $leave = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/leave-requests" -Session $session -Headers $csrf `
        -Body @{ startDate = $leaveDate; endDate = $leaveDate; reason = "QA leave workflow" }) 201 "Leave request create"
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/organizations/$organizationId/attendance/leave-requests/$($leave.Json.data.id)/decision" -Session $session -Headers $csrf `
        -Body @{ decision = "approve"; note = "QA approval" }) 200 "Leave request approval" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/organizations/$organizationId/attendance/reports/daily?date=$today" -Session $session) 200 "Daily attendance report" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/organizations/$organizationId/attendance/reports/monthly?month=$((Get-Date).ToString('yyyy-MM'))" -Session $session) 200 "Monthly attendance report" | Out-Null

    # Phase 3: project, membership, work resources and reorder.
    $project = Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects" -Session $session -Headers $csrf `
        -Body @{ name = "QA Project $runId"; description = "End-to-end phase smoke"; status = "active"; icon = "Q"; color = "blue"; ownerId = $qaUserId; organizationId = $organizationId; legacyId = "qa-$runId" }) 201 "Project create"
    $projectId = $project.Json.data.id
    Expect-Status (Invoke-Api -Method PUT -Path "/api/v1/projects/$projectId/settings/dashboard" -Session $session -Headers $csrf `
        -Body @{ layout = "compact"; widgets = @("tasks", "bugs", "risks") }) 200 "Project settings upsert" | Out-Null
    Expect-Status (Invoke-Api -Method PUT -Path "/api/v1/projects/$projectId/roles/developer" -Session $session -Headers $csrf `
        -Body @{ name = "Developer"; permissions = @("tasks:read", "tasks:update") }) 200 "Project role upsert" | Out-Null
    Expect-Status (Invoke-Api -Method PUT -Path "/api/v1/projects/$projectId/members/$qaUserId" -Session $session -Headers $csrf `
        -Body @{ memberId = $qaUserId; roles = @("Developer") }) 200 "Project membership upsert" | Out-Null

    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/task-columns" -Session $session -Headers $csrf `
        -Body @{ legacyId = "qa-todo"; title = "QA To do"; order = 1 }) 201 "Task column create" | Out-Null
    Expect-Status (Invoke-Api -Method PUT -Path "/api/v1/projects/$projectId/tasks/QA-TASK-$runId" -Session $session -Headers $csrf `
        -Body @{ title = "QA task $runId"; status = "todo"; order = 1; assigneeId = $qaUserId; points = 3 }) 200 "Task upsert" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/sprints" -Session $session -Headers $csrf `
        -Body @{ legacyId = "QA-SPRINT-$runId"; name = "QA Sprint"; status = "planned" }) 201 "Sprint create" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/bug-columns" -Session $session -Headers $csrf `
        -Body @{ legacyId = "qa-open"; title = "QA Open"; order = 1 }) 201 "Bug column create" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/bugs" -Session $session -Headers $csrf `
        -Body @{ legacyId = "QA-BUG-$runId"; title = "QA bug"; status = "open"; order = 1; priority = "high" }) 201 "Bug create" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/tasks/reorder" -Session $session -Headers $csrf `
        -Body @(@{ id = "QA-TASK-$runId"; order = 2; status = "in-progress" })) 200 "Task reorder" | Out-Null
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/bugs/reorder" -Session $session -Headers $csrf `
        -Body @(@{ id = "QA-BUG-$runId"; order = 2; status = "in-progress" })) 200 "Bug reorder" | Out-Null

    # Phase 4: operations, knowledge, MinIO, comments, notifications, activity/audit.
    foreach ($resource in @(
        @{ name = "risks"; body = @{ legacyId = "QA-RISK-$runId"; description = "QA delivery risk"; status = "open"; level = "high" } },
        @{ name = "budget-items"; body = @{ legacyId = "QA-BUDGET-$runId"; category = "QA budget"; icon = "Q"; budget = 1250.50; spent = 250.25 } },
        @{ name = "timeline-phases"; body = @{ legacyId = "QA-PHASE-$runId"; name = "QA phase"; rowLabel = "QA phase"; label = "QA phase"; leftPercent = 10; widthPercent = 25; color = "accent"; status = "active" } },
        @{ name = "meetings"; body = @{ legacyId = "QA-MEETING-$runId"; title = "QA meeting"; status = "scheduled" } },
        @{ name = "folders"; body = @{ legacyId = "QA-FOLDER-$runId"; name = "QA folder" } },
        @{ name = "documents"; body = @{ legacyId = "QA-DOC-$runId"; name = "QA document"; title = "QA document"; type = "Other"; icon = "Q"; size = "smoke"; badge = @{ label = "Active"; variant = "accent" } } },
        @{ name = "wikis"; body = @{ legacyId = "QA-WIKI-$runId"; title = "QA wiki"; content = "Verified" } },
        @{ name = "notifications"; body = @{ legacyId = "QA-NOTIFY-$runId"; title = "QA notification"; read = $false; userId = $qaUserId } }
    )) {
        Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/$($resource.name)" -Session $session -Headers $csrf -Body $resource.body) 201 "$($resource.name) create" | Out-Null
    }

    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/tasks/QA-TASK-$runId/comments" -Session $session -Headers $csrf `
        -Body @{ legacyId = "QA-COMMENT-$runId"; text = "QA nested comment" }) 201 "Nested comment create" | Out-Null

    $attachmentFile = Join-Path ([System.IO.Path]::GetTempPath()) "project-os-$runId.txt"
    Set-Content -LiteralPath $attachmentFile -Value "ProjectOS MinIO smoke $runId" -NoNewline
    $storagePath = "projects/$projectId/documents/QA-DOC-$runId/project-os-$runId.txt"
    $upload = Send-MultipartFile -Uri "$BaseUrl/api/v1/projects/$projectId/attachments/content?storagePath=$([uri]::EscapeDataString($storagePath))" `
        -FilePath $attachmentFile -Session $session -Headers $csrf
    Expect-Status $upload 200 "MinIO upload" | Out-Null
    $uploadBody = $upload.Raw | ConvertFrom-Json
    $storagePath = $uploadBody.data.storagePath
    Assert-That ($storagePath -like "projects/$projectId/*") "MinIO must return a project-scoped object key"
    $download = Invoke-WebRequest -Uri "$BaseUrl/api/v1/projects/$projectId/attachments/content?storagePath=$([uri]::EscapeDataString($storagePath))" `
        -Method GET -WebSession $session -UseBasicParsing
    Expect-Status ([pscustomobject]@{ Status = [int]$download.StatusCode; Raw = $download.Content }) 200 "MinIO download" | Out-Null
    Assert-That ($download.Content -match $runId) "Downloaded MinIO object must match uploaded content"

    Start-Sleep -Seconds 2
    $activities = Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/activities?size=100" -Session $session) 200 "Activity feed and audit outbox"
    Assert-That ($activities.Json.meta.total -gt 0) "Mutations must produce immutable activity events"
    Expect-Status (Invoke-Api -Method POST -Path "/api/v1/projects/$projectId/activities" -Session $session -Headers $csrf -Body @{ title = "must fail" }) 405 "Immutable activity write rejection" | Out-Null

    # Phase 5: read models, export, cache HIT and immediate invalidation.
    $dashboardMiss = Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/read-model/dashboard" -Session $session) 200 "Dashboard read model MISS"
    Assert-That ($dashboardMiss.Headers["X-ProjectOS-Cache"] -in @("MISS", "BYPASS")) "First dashboard read must be MISS or BYPASS"
    Assert-That ($dashboardMiss.Json.data.summary.tasks -eq 1) "Dashboard must aggregate the persisted task"
    $dashboardHit = Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/read-model/dashboard" -Session $session) 200 "Dashboard read model HIT"
    Assert-That ($dashboardHit.Headers["X-ProjectOS-Cache"] -eq "HIT") "Second dashboard read must be a Redis HIT"
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/read-model/workload" -Session $session) 200 "Workload read model" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/read-model/reports/tasks" -Session $session) 200 "Tasks report read model" | Out-Null
    $csv = Invoke-WebRequest -Uri "$BaseUrl/api/v1/projects/$projectId/read-model/reports/tasks/export.csv" -Method GET -WebSession $session -UseBasicParsing
    Expect-Status ([pscustomobject]@{ Status = [int]$csv.StatusCode; Raw = $csv.Content }) 200 "CSV report export" | Out-Null
    Assert-That ($csv.Headers["Content-Disposition"] -match "attachment") "CSV export must set Content-Disposition"
    Assert-That ($csv.Content -match "QA task") "CSV export must contain the persisted task"

    Expect-Status (Invoke-Api -Method PATCH -Path "/api/v1/projects/$projectId/tasks/QA-TASK-$runId" -Session $session -Headers $csrf `
        -Body @{ status = "done" }) 200 "Task mutation for cache invalidation" | Out-Null
    $dashboardAfterMutation = Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/read-model/dashboard" -Session $session) 200 "Dashboard cache invalidation"
    Assert-That ($dashboardAfterMutation.Headers["X-ProjectOS-Cache"] -in @("MISS", "BYPASS")) "Mutation must invalidate the project read-model cache"
    Assert-That ($dashboardAfterMutation.Json.data.summary.taskStatus.done -eq 1) "Invalidated read model must return fresh task status"

    # Persistence evidence: read the same entities back through a new request path.
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId" -Session $session) 200 "Project persistence reload" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/tasks/QA-TASK-$runId" -Session $session) 200 "Task persistence reload" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/projects/$projectId/documents/QA-DOC-$runId" -Session $session) 200 "Document persistence reload" | Out-Null
    Expect-Status (Invoke-Api -Method GET -Path "/api/v1/users/me/profile" -Session $session) 200 "Profile persistence reload" | Out-Null

    Write-Host ""
    Write-Host "ALL PHASES RUNTIME SMOKE PASSED"
    Write-Host "QA project: $projectId"
    Write-Host "QA organization: $organizationId"
}
finally {
    if ($KeepData) {
        Write-Host "KeepData enabled; QA records were preserved for browser inspection."
    }
    else {
        # Clean reversible resources through their owning APIs. Immutable activity events remain as audit history.
        if ($null -ne $projectId) {
            $csrf = Get-CsrfHeader $session
            if ($storagePath) {
                Invoke-WebRequest -Uri "$BaseUrl/api/v1/projects/$projectId/attachments/content?storagePath=$([uri]::EscapeDataString($storagePath))" `
                    -Method DELETE -WebSession $session -Headers $csrf -UseBasicParsing | Out-Null
            }
            foreach ($target in @(
                "tasks/QA-TASK-$runId/comments/QA-COMMENT-$runId",
                "notifications/QA-NOTIFY-$runId", "wikis/QA-WIKI-$runId", "documents/QA-DOC-$runId",
                "folders/QA-FOLDER-$runId", "meetings/QA-MEETING-$runId", "timeline-phases/QA-PHASE-$runId",
                "budget-items/QA-BUDGET-$runId", "risks/QA-RISK-$runId", "bugs/QA-BUG-$runId",
                "bug-columns/qa-open", "sprints/QA-SPRINT-$runId", "tasks/QA-TASK-$runId", "task-columns/qa-todo"
            )) {
                Invoke-Api -Method DELETE -Path "/api/v1/projects/$projectId/$target" -Session $session -Headers $csrf | Out-Null
            }
            Invoke-Api -Method DELETE -Path "/api/v1/projects/$projectId" -Session $session -Headers $csrf | Out-Null
        }
        if ($null -ne $organizationId) {
            $csrf = Get-CsrfHeader $session
            Invoke-Api -Method PATCH -Path "/api/v1/organizations/$organizationId" -Session $session -Headers $csrf -Body @{ status = "inactive" } | Out-Null
        }
        $csrf = Get-CsrfHeader $session
        Invoke-Api -Method DELETE -Path "/api/v1/users/me" -Session $session -Headers $csrf -Body @{ password = $qaPassword } | Out-Null
    }
    if ($attachmentFile -and (Test-Path -LiteralPath $attachmentFile)) { Remove-Item -LiteralPath $attachmentFile -Force }
}
