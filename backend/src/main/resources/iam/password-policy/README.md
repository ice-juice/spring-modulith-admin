# Login password blocklist v1

Source: https://github.com/danielmiessler/SecLists/blob/master/Passwords/Common-Credentials/10k-most-common.txt
Retrieved: 2026-11-11. License: MIT (see LICENSE-SecLists.txt).
Source SHA-256: 68782d6a4a19a4768d5f15dd66bd534e7a33055cc755411e33f16d18c50fdcce

Derived deterministically: retain ASCII alphanumeric entries of length 8 through 12, lowercase, deduplicate and sort; add password1/password123/admin123/qwerty12/abc12345/123456ab as project regression cases. LF UTF-8 with final newline. The checksum file pins the exact derived bytes and is checked at startup. No user passwords or password hashes are sent to any external service. This finite list is supplemented by repetition and account-name rules and does not claim exhaustive weak-password detection.
