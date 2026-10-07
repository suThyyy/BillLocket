[CmdletBinding()]
param(
    [string]$Worktree = "D:\ForIntern\BillLocket-opencode"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Worktree -PathType Container)) {
    throw "OpenCode worktree not found: $Worktree"
}

$resolvedWorktree = (Resolve-Path -LiteralPath $Worktree).Path
$expectedBranch = "worker/opencode-test"
$actualBranch = (& git -c "safe.directory=$resolvedWorktree" -C $resolvedWorktree branch --show-current).Trim()

if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect the OpenCode worktree: $resolvedWorktree"
}

if ($actualBranch -ne $expectedBranch) {
    throw "OpenCode worktree is on '$actualBranch'; expected '$expectedBranch'."
}

$requiredFiles = @(
    "AGENTS.md",
    "docs/ORCHESTRATION.md",
    "docs/WORKERS.md",
    "docs/TASKS.md",
    "docs/HANDOFF.md"
)

foreach ($relativePath in $requiredFiles) {
    $requiredPath = Join-Path $resolvedWorktree $relativePath
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required worker instruction file is missing: $requiredPath"
    }
}

$openCodeCommand = Get-Command opencode -ErrorAction SilentlyContinue
if (-not $openCodeCommand) {
    throw "The opencode CLI is not available in PATH."
}

$workerPrompt = @"
You are the OpenCode external worker for the BillLocket repository.

Work only in: $resolvedWorktree

Before doing anything, read AGENTS.md, docs/ORCHESTRATION.md, docs/WORKERS.md, docs/TASKS.md, and docs/HANDOFF.md. Execute only the assignment under the '## OpenCode' section of docs/WORKERS.md.

Follow the assignment's Scope, Do Not Touch, Required Verification, and Expected Output exactly. Do not commit, push, merge, rebase, cherry-pick, or start another task. Stop with a final state of NEEDS_REVIEW or BLOCKED.
"@

Push-Location $resolvedWorktree
try {
    & $openCodeCommand.Source run $workerPrompt
    $workerExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($workerExitCode -ne 0) {
    throw "OpenCode worker exited with code $workerExitCode."
}
