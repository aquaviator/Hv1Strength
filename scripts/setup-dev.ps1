# -----------------------------------------------------------------------------
# Human Exercise Catalogue - Development Environment Bootstrap
# -----------------------------------------------------------------------------

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "==========================================="
Write-Host " Human Exercise Catalogue Setup"
Write-Host "==========================================="
Write-Host ""

# Ensure we're running from the repository root
if (!(Test-Path ".git")) {
    Write-Error "Run this script from the repository root."
    exit 1
}

# Check Python
Write-Host "Checking Python installation..."
py --version

# Create virtual environment if required
if (!(Test-Path ".\.venv\Scripts\python.exe")) {

    Write-Host ""
    Write-Host "Creating virtual environment..."
    py -3.12 -m venv .venv

}
else {

    Write-Host ""
    Write-Host "Virtual environment already exists."

}

# Upgrade pip
Write-Host ""
Write-Host "Upgrading pip..."
.\.venv\Scripts\python.exe -m pip install --upgrade pip

# Install development dependencies
Write-Host ""
Write-Host "Installing development dependencies..."
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt

# Run regression suite
Write-Host ""
Write-Host "Running regression tests..."
.\.venv\Scripts\python.exe -m pytest `
    tests/test_validate_catalogue.py `
    tests/test_validate_catalogue_v2.py

# Validate catalogue
Write-Host ""
Write-Host "Validating Schema v1..."
.\.venv\Scripts\python.exe `
    tools/catalogue/validate_catalogue.py `
    --schema-version 1

Write-Host ""
Write-Host "Validating Schema v2..."
.\.venv\Scripts\python.exe `
    tools/catalogue/validate_catalogue.py `
    --schema-version 2

Write-Host ""
Write-Host "==========================================="
Write-Host " Development environment ready."
Write-Host "==========================================="