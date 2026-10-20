#!/usr/bin/env node
// The J2C MCP server on stdio: j2c mcp, rooted at the working directory
// unless --root is given. Other arguments (--project, --no-build-tool) are
// passed to j2c mcp.
require("./launch").launch(["mcp", ...process.argv.slice(2)]);
