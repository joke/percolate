<!-- rtk-instructions v2 -->
## Golden Rule

**Always use jcodemunch-mcp for code lookup** whenever available. Prefer symbol search, outlines, and targeted retrieval over reading full files.

**Always prefix commands with `rtk`**. If RTK has a dedicated filter, it uses it. If not, it passes through unchanged. This means RTK is always safe to use.

**Important**: Even in command chains with `&&`, use `rtk`:
```bash
# ❌ Wrong
git add . && git commit -m "msg" && git push

# ✅ Correct
rtk git add . && rtk git commit -m "msg" && rtk git push
```
