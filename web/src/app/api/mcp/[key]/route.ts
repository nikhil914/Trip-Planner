import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { authenticateConnectorKey, connectorOverLimit } from "@/server/lib/connector-keys";
import { buildMcpServer } from "@/server/lib/mcp-server";

export const maxDuration = 60;

// The secret in the path is the credential, because custom connectors are added as a plain URL.
// Each request gets its own server and transport, so nothing is kept between calls.
async function handle(request: Request, { params }: RouteContext<"/api/mcp/[key]">): Promise<Response> {
  const { key } = await params;
  const user = await authenticateConnectorKey(key);
  if (!user) {
    return Response.json(
      { jsonrpc: "2.0", error: { code: -32001, message: "This connector link was revoked, expired or is wrong. Create a new one on the trip planner." }, id: null },
      { status: 401 },
    );
  }
  if (await connectorOverLimit(user.keyId)) {
    return Response.json(
      { jsonrpc: "2.0", error: { code: -32002, message: "Too many calls on this connector link. Wait 10 minutes." }, id: null },
      { status: 429, headers: { "Retry-After": "600" } },
    );
  }
  const origin = process.env.PUBLIC_URL?.replace(/\/+$/, "") || new URL(request.url).origin;
  const server = buildMcpServer(user.id, origin);
  const transport = new WebStandardStreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
  await server.connect(transport);
  try {
    return await transport.handleRequest(request);
  } finally {
    void server.close();
  }
}

export const POST = handle;
export const GET = handle;
export const DELETE = handle;
