[CmdletBinding()]
param(
    [string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'

if (-not $EvidenceDirectory) {
    $latestEvidence = Get-ChildItem -Directory (Join-Path $PSScriptRoot 'evidence\backup-restore-*') |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latestEvidence) {
        throw 'No backup evidence directory was found.'
    }
    $EvidenceDirectory = $latestEvidence.FullName
}

$EvidenceDirectory = (Resolve-Path -LiteralPath $EvidenceDirectory).Path
$postgresDump = Join-Path $EvidenceDirectory 'postgres.dump'
$minioArchive = Join-Path $EvidenceDirectory 'minio-data.tar.gz'
if (-not (Test-Path -LiteralPath $postgresDump) -or -not (Test-Path -LiteralPath $minioArchive)) {
    throw 'The evidence directory must contain postgres.dump and minio-data.tar.gz.'
}

$postgresContainer = 'project-os-platform-postgres-1'
$stamp = [IO.Path]::GetFileName($EvidenceDirectory).Replace('backup-restore-', '').Replace('-', '_')
$temporaryDatabase = "project_os_restore_qa_$stamp"
$temporaryVolume = "project_os_minio_restore_qa_$stamp"
$containerDump = '/tmp/project-os-restore-proof.dump'
$dockerMount = "${EvidenceDirectory}:/backup"

if ($temporaryDatabase -notlike 'project_os_restore_qa_*' -or $temporaryVolume -notlike 'project_os_minio_restore_qa_*') {
    throw 'Refusing to operate on an unrecognized temporary resource name.'
}

docker cp $postgresDump "${postgresContainer}:${containerDump}" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not copy the PostgreSQL archive into the container.' }

docker exec $postgresContainer dropdb -U project_os_owner --if-exists $temporaryDatabase | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not pre-clean the temporary restore database.' }
docker exec $postgresContainer createdb -U project_os_owner $temporaryDatabase
if ($LASTEXITCODE -ne 0) { throw 'Could not create the temporary restore database.' }

try {
    docker exec $postgresContainer pg_restore -U project_os_owner --no-owner --no-privileges -d $temporaryDatabase $containerDump
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL restore into the temporary database failed.' }

    $tableInventorySql = @"
SELECT table_schema || '|' || table_name
FROM information_schema.tables
WHERE table_type = 'BASE TABLE'
  AND table_schema IN ('identity','organization','attendance','project','work','operations','knowledge','activity','migration')
ORDER BY table_schema, table_name;
"@
    $tables = @(docker exec $postgresContainer psql -v ON_ERROR_STOP=1 -U project_os_owner -d project_os -At -c $tableInventorySql)
    if ($LASTEXITCODE -ne 0 -or $tables.Count -eq 0) { throw 'Could not inventory source tables.' }

    $mismatches = @()
    $totalRows = 0L
    $nonEmptyBusinessTables = @()
    foreach ($entry in $tables) {
        $parts = $entry -split '\|', 2
        $schema = $parts[0]
        $table = $parts[1]
        if ($schema -notmatch '^[a-z_]+$' -or $table -notmatch '^[a-z_]+$') {
            throw "Unsafe table identifier returned by PostgreSQL: $entry"
        }

        $countSql = 'SELECT count(*) FROM "{0}"."{1}";' -f $schema, $table
        $sourceCount = docker exec $postgresContainer psql -v ON_ERROR_STOP=1 -U project_os_owner -d project_os -At -c $countSql
        if ($LASTEXITCODE -ne 0) { throw "Source row count failed for $entry." }
        $restoredCount = docker exec $postgresContainer psql -v ON_ERROR_STOP=1 -U project_os_owner -d $temporaryDatabase -At -c $countSql
        if ($LASTEXITCODE -ne 0) { throw "Restored row count failed for $entry." }

        if ([long]$sourceCount -ne [long]$restoredCount) {
            $mismatches += "$entry source=$sourceCount restored=$restoredCount"
        }
        $totalRows += [long]$sourceCount
        if ([long]$sourceCount -gt 0 -and $table -ne 'flyway_schema_history') {
            $nonEmptyBusinessTables += "$entry=$sourceCount"
        }
    }
    if ($mismatches.Count -gt 0) {
        throw "Restored row counts differ: $($mismatches -join '; ')"
    }

    docker volume create $temporaryVolume | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the temporary MinIO restore volume.' }
    docker run --rm -v "${temporaryVolume}:/restore" -v $dockerMount alpine:3.21 sh -c 'cd /restore && tar -xzf /backup/minio-data.tar.gz'
    if ($LASTEXITCODE -ne 0) { throw 'MinIO restore into the temporary volume failed.' }
    $minioFiles = docker run --rm -v "${temporaryVolume}:/restore:ro" alpine:3.21 sh -c 'find /restore -type f | wc -l'
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify the temporary MinIO restore volume.' }

    "DATABASE_RESTORE_TABLES_VERIFIED=$($tables.Count)"
    "DATABASE_RESTORE_TOTAL_ROWS_VERIFIED=$totalRows"
    "MINIO_RESTORE_FILES_VERIFIED=$minioFiles"
    'NON_EMPTY_BUSINESS_TABLES:'
    $nonEmptyBusinessTables
    'BACKUP_RESTORE_EXACT_PROOF=PASS'
}
finally {
    docker exec $postgresContainer dropdb -U project_os_owner --if-exists $temporaryDatabase | Out-Null
    docker exec $postgresContainer rm -f $containerDump | Out-Null
    docker volume rm -f $temporaryVolume 2>$null | Out-Null
}
