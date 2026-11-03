$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$tmp  = Join-Path $env:TEMP 'manucore-smoke'
New-Item -ItemType Directory -Force $tmp | Out-Null
$run  = Get-Date -Format 'HHmmss'
$script:pass = 0
$script:fail = 0

# ---------- helpers (all HTTP goes through the real curl.exe) ----------

function Api([string]$Method, [string]$Path, [string]$Token, [string]$Body) {
    Remove-Item "$tmp\resp.txt" -ErrorAction SilentlyContinue
    $a = @('-s', '-o', "$tmp\resp.txt", '-w', '%{http_code}', '-X', $Method, "$base$Path")
    if ($Token) { $a += @('-H', "Authorization: Bearer $Token") }
    if ($Body) {
        [IO.File]::WriteAllText("$tmp\body.json", $Body, [Text.UTF8Encoding]::new($false))
        $a += @('-H', 'Content-Type: application/json', '--data-binary', "@$tmp\body.json")
    }
    $code = & curl.exe @a
    $text = $null
    if (Test-Path "$tmp\resp.txt") { $text = Get-Content "$tmp\resp.txt" -Raw }
    $json = $null
    if ($text) { try { $json = $text | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Status = [int]$code; Json = $json; Text = $text }
}

function Check([string]$Name, $Actual, $Expected) {
    if ("$Actual" -eq "$Expected") {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $Name   expected=[$Expected] actual=[$Actual]" -ForegroundColor Red
        $script:fail++
    }
}

function Section([string]$Title) { Write-Host "`n== $Title" -ForegroundColor Cyan }

function PolicyJson($name, $action, $resource, $effect, $priority, $role, $groups = '[]', $desc = 'smoke test') {
@"
{ "name": "$name", "description": "$desc", "action": "$action", "resource": "$resource",
  "effect": "$effect", "priority": "$priority",
  "subjects": [ { "type": "ROLE", "id": "$role" } ],
  "conditionGroups": $groups }
"@
}

$P = '/api/v1/authorization/policies'
$ALLDAY = '[{"operator":"AND","conditions":[{"attribute":"TIME","operator":"BETWEEN","value":"00:00,23:59:59.999999999"}]}]'

# ---------- wait for the stack ----------

Section 'Waiting for the gateway'
for ($i = 0; $i -lt 60; $i++) {
    $r = Api GET '/api/v1/auth/jwks'
    if ($r.Status -eq 200) { break }
    Start-Sleep 5
}
Check 'gateway reaches identity' $r.Status 200
if ($r.Status -ne 200) { Write-Host 'Stack is not up. Aborting.' -ForegroundColor Red; exit 1 }

# ---------- authentication ----------

Section 'Authentication'
$r = Api POST '/api/v1/auth/login' $null '{"tenantId":"acme","email":"admin@acme.com","password":"wrong"}'
Check 'wrong password -> 401' $r.Status 401

$r = Api POST '/api/v1/auth/login' $null '{"tenantId":"acme","email":"admin@acme.com","password":"Admin@123"}'
Check 'admin login -> 200' $r.Status 200
$admin = $r.Json.accessToken
if (-not $admin) { Write-Host 'No admin token. Aborting.' -ForegroundColor Red; exit 1 }

$r = Api GET '/api/v1/inventory/ping'
Check 'no token -> 401' $r.Status 401

$r = Api GET '/api/v1/inventory/ping' 'not.a.token'
Check 'garbage token -> 401' $r.Status 401

Write-Host '  waiting for authorization-service to be ready...'
for ($i = 0; $i -lt 60; $i++) {
    $r = Api GET '/api/v1/auth/me' $admin
    if ($r.Json.permissionsAvailable) { break }
    Start-Sleep 5
}

$r = Api GET '/api/v1/inventory/ping' $admin
Check 'admin ping through gateway -> 200' $r.Status 200

# ---------- admin profile + matrix ----------

Section 'Admin profile and permission matrix'
$r = Api GET '/api/v1/auth/me' $admin
Check '/me -> 200' $r.Status 200
Check 'permissions available' $r.Json.permissionsAvailable $true
Check 'canManagePolicies' $r.Json.permissions.canManagePolicies $true
Check 'matrix: admin can CREATE items' $r.Json.permissions.matrix.'INVENTORY.ITEMS'.CREATE $true
Check 'matrix: admin can DELETE policies' $r.Json.permissions.matrix.'AUTHORIZATION.POLICIES'.DELETE $true

$r = Api GET '/api/v1/authorization/permissions' $admin
Check '/permissions directly -> 200' $r.Status 200
Check '/permissions lists flat allowed entries' ($r.Json.allowed -contains 'INVENTORY.ITEMS:READ') $true

# ---------- cleanup from earlier runs ----------

Section 'Cleaning leftovers from earlier runs'
$r = Api GET "$P`?size=100" $admin
foreach ($p in $r.Json.items) {
    if ($p.name -like 'smoke-*') { Api DELETE "$P/$($p.id)" $admin | Out-Null }
}

# ---------- policy CRUD ----------

Section 'Policy management (admin)'
$r = Api GET $P $admin
Check 'list policies -> 200' $r.Status 200
Check 'seeded policies present (>= 11)' ($r.Json.totalElements -ge 11) $true

$r = Api GET '/api/v1/authorization/catalog' $admin
Check 'catalog -> 200' $r.Status 200
Check 'catalog has wildcard action' ($r.Json.actions -contains '*') $true
Check 'catalog has INVENTORY.ITEMS' ($r.Json.resources -contains 'INVENTORY.ITEMS') $true

$name = "smoke-crud-$run"
$body = PolicyJson $name 'READ' 'INVENTORY.ITEMS' 'ALLOW' 'LOW' 'AUDITOR'
$r = Api POST $P $admin $body
Check 'create policy -> 201' $r.Status 201
$policyId = $r.Json.id

$r = Api POST $P $admin $body
Check 'duplicate name -> 409' $r.Status 409

$r = Api POST $P $admin (PolicyJson "$name-x" 'FLY' 'INVENTORY.ITEMS' 'ALLOW' 'LOW' 'AUDITOR')
Check 'unknown action -> 400' $r.Status 400

$bad = '[{"operator":"AND","conditions":[{"attribute":"TIME","operator":"BETWEEN","value":"09:00"}]}]'
$r = Api POST $P $admin (PolicyJson "$name-bad" 'READ' 'INVENTORY.ITEMS' 'ALLOW' 'LOW' 'AUDITOR' $bad)
Check 'malformed BETWEEN -> 400' $r.Status 400

$r = Api GET "$P/$policyId" $admin
Check 'get policy -> 200' $r.Status 200
Check 'role stored upper-case' $r.Json.subjects[0].id 'AUDITOR'

$upd = PolicyJson $name 'READ' 'INVENTORY.ITEMS' 'ALLOW' 'LOW' 'AUDITOR' $ALLDAY 'updated'
$r = Api PUT "$P/$policyId" $admin $upd
Check 'update policy -> 200' $r.Status 200
Check 'update persisted description' $r.Json.description 'updated'
Check 'update persisted condition group' $r.Json.conditionGroups.Count 1

$r = Api GET "$P/00000000-0000-0000-0000-000000000000" $admin
Check 'unknown policy -> 404' $r.Status 404

$r = Api DELETE "$P/$policyId" $admin
Check 'delete policy -> 204' $r.Status 204
$r = Api GET "$P/$policyId" $admin
Check 'deleted policy -> 404' $r.Status 404

# ---------- inventory through the gateway ----------

Section 'Inventory through the gateway (admin)'
$sku = "smoke-$run"
$create = '{"sku":"' + $sku + '","name":"Smoke bolt","unit":"pcs","quantity":10}'
$r = Api POST '/api/v1/inventory/items' $admin $create
Check 'create item -> 201' $r.Status 201
$itemId = $r.Json.id
Check 'sku normalized to upper-case' $r.Json.sku $sku.ToUpper()
Check 'tenant comes from the token' $r.Json.tenantId 'acme'

$r = Api POST '/api/v1/inventory/items' $admin $create
Check 'duplicate sku -> 409' $r.Status 409

$r = Api GET '/api/v1/inventory/items' $admin
Check 'list items -> 200' $r.Status 200
$r = Api GET "/api/v1/inventory/items/$itemId" $admin
Check 'get item -> 200' $r.Status 200

$r = Api PUT "/api/v1/inventory/items/$itemId" $admin '{"name":"Renamed","unit":"box"}'
Check 'update item -> 200' $r.Status 200
Check 'update persisted' $r.Json.name 'Renamed'

$r = Api POST "/api/v1/inventory/items/$itemId/adjust" $admin '{"delta":5}'
Check 'adjust +5 -> 200' $r.Status 200
Check 'quantity is 15' $r.Json.quantity 15
$r = Api POST "/api/v1/inventory/items/$itemId/adjust" $admin '{"delta":-1000}'
Check 'adjust below zero -> 409' $r.Status 409

$r = Api POST "/api/v1/inventory/items/$itemId" $admin '{"x":1}'
Check 'unmapped request shape is denied -> 403' $r.Status 403

# ---------- deny policies enforced ----------

Section 'Deny policies are enforced at the gateway'
$r = Api POST $P $admin (PolicyJson "smoke-deny-delete-$run" 'DELETE' 'INVENTORY.ITEMS' 'DENY' 'HIGH' 'INVENTORY_MANAGER')
Check 'create DENY delete -> 201' $r.Status 201
$deny1 = $r.Json.id
$r = Api POST $P $admin (PolicyJson "smoke-deny-adjust-$run" 'ADJUST' 'INVENTORY.ITEMS' 'DENY' 'HIGH' 'INVENTORY_MANAGER' $ALLDAY)
Check 'create conditional DENY adjust -> 201' $r.Status 201
$deny2 = $r.Json.id

$r = Api DELETE "/api/v1/inventory/items/$itemId" $admin
Check 'delete item blocked -> 403' $r.Status 403
$r = Api POST "/api/v1/inventory/items/$itemId/adjust" $admin '{"delta":1}'
Check 'adjust blocked by conditional deny -> 403' $r.Status 403
$r = Api GET "/api/v1/inventory/items/$itemId" $admin
Check 'read still allowed -> 200' $r.Status 200

$r = Api GET '/api/v1/auth/me' $admin
Check 'matrix: DELETE now false' $r.Json.permissions.matrix.'INVENTORY.ITEMS'.DELETE $false
Check 'matrix: ADJUST now false' $r.Json.permissions.matrix.'INVENTORY.ITEMS'.ADJUST $false
Check 'matrix: READ still true' $r.Json.permissions.matrix.'INVENTORY.ITEMS'.READ $true

$r = Api DELETE "$P/$deny1" $admin
Check 'remove deny delete -> 204' $r.Status 204
$r = Api DELETE "$P/$deny2" $admin
Check 'remove deny adjust -> 204' $r.Status 204

$r = Api POST "/api/v1/inventory/items/$itemId/adjust" $admin '{"delta":1}'
Check 'adjust allowed again -> 200' $r.Status 200
$r = Api DELETE "/api/v1/inventory/items/$itemId" $admin
Check 'delete item allowed again -> 204' $r.Status 204
$r = Api GET "/api/v1/inventory/items/$itemId" $admin
Check 'deleted item -> 404' $r.Status 404

# ---------- non-admin ----------

Section 'Non-admin user (viewer@acme.com)'
$r = Api POST '/api/v1/auth/login' $null '{"tenantId":"acme","email":"viewer@acme.com","password":"Viewer@123"}'
if ($r.Status -ne 200) {
    Write-Host '  SKIPPED: viewer user not found. Run step 1 first.' -ForegroundColor Yellow
} else {
    $v = $r.Json.accessToken
    Check 'viewer login -> 200' $r.Status 200

    $r = Api GET $P $v
    Check 'viewer cannot list policies -> 403' $r.Status 403
    $r = Api POST $P $v (PolicyJson "smoke-viewer-hack-$run" 'READ' 'INVENTORY.ITEMS' 'ALLOW' 'CRITICAL' 'VIEWER')
    Check 'viewer cannot create policies -> 403' $r.Status 403
    $r = Api GET '/api/v1/authorization/catalog' $v
    Check 'viewer cannot read catalog -> 403' $r.Status 403
    $r = Api GET '/api/v1/inventory/items' $v
    Check 'no policy means default deny -> 403' $r.Status 403

    $r = Api GET '/api/v1/authorization/permissions' $v
    Check 'viewer /permissions -> 200' $r.Status 200
    Check 'viewer canManagePolicies is false' $r.Json.canManagePolicies $false
    Check 'viewer matrix: READ items false' $r.Json.matrix.'INVENTORY.ITEMS'.READ $false

    $r = Api POST $P $admin (PolicyJson "smoke-viewer-read-$run" 'READ' 'INVENTORY.ITEMS' 'ALLOW' 'MEDIUM' 'VIEWER')
    Check 'admin grants viewer READ -> 201' $r.Status 201
    $grant = $r.Json.id

    $r = Api GET '/api/v1/inventory/items' $v
    Check 'viewer can now read items -> 200' $r.Status 200
    $r = Api POST '/api/v1/inventory/items' $v ('{"sku":"viewer-' + $run + '","name":"x","unit":"pcs","quantity":1}')
    Check 'viewer still cannot create items -> 403' $r.Status 403

    $r = Api GET '/api/v1/authorization/permissions' $v
    Check 'viewer matrix: READ true' $r.Json.matrix.'INVENTORY.ITEMS'.READ $true
    Check 'viewer matrix: CREATE false' $r.Json.matrix.'INVENTORY.ITEMS'.CREATE $false

    $r = Api DELETE "$P/$grant" $admin
    Check 'admin revokes grant -> 204' $r.Status 204
    $r = Api GET '/api/v1/inventory/items' $v
    Check 'access revoked immediately -> 403' $r.Status 403
}

# ---------- summary ----------

$color = 'Green'
if ($script:fail -gt 0) { $color = 'Red' }
Write-Host "`n$($script:pass) passed, $($script:fail) failed" -ForegroundColor $color
exit $script:fail