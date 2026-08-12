# Strength exercise catalogue release package

`app/src/main/assets/strength-exercise-catalogue.json` is the offline runtime source of truth for governed exercises. Contract version 1 hashes the UTF-8 bytes of the compact JSON `exercises` array, preserving array and object-key order. Exercise IDs and payload ordering are stable.

After an intentional payload edit, run `powershell -File scripts/update-strength-catalogue.ps1`. The script updates the declared count and SHA-256 deterministically. Runtime validation completes before any Room writes; an invalid package retains the existing library and activates a three-exercise safe fallback only when no usable governed rows exist.
