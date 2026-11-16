import { createServer } from "node:http";

const PORT = Number(process.env.FAKE_FEEDS_PORT ?? 4010);
const LOG4J = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1";
const MODIFIED = "2024-01-01T00:00:00Z";

const KEV_FEED = {
  title: "CISA Catalog of Known Exploited Vulnerabilities (fake)",
  catalogVersion: "e2e",
  count: 1,
  vulnerabilities: [
    {
      cveID: "CVE-2021-44228",
      vendorProject: "Apache",
      product: "Log4j2",
      vulnerabilityName: "Apache Log4j2 Remote Code Execution Vulnerability",
      dateAdded: "2021-12-10",
      shortDescription: "Apache Log4j2 contains a vulnerability where JNDI features do not protect against attacker-controlled JNDI-related endpoints.",
      requiredAction: "Apply updates per vendor instructions.",
      dueDate: "2021-12-24",
      knownRansomwareCampaignUse: "Known",
    },
  ],
};

const LOG4SHELL = {
  id: "GHSA-jfh8-c2jp-5v3q",
  summary: "Remote code injection in Log4j",
  aliases: ["CVE-2021-44228"],
  published: "2021-12-10T00:00:00Z",
  modified: MODIFIED,
  database_specific: { severity: "CRITICAL" },
};

// Responde con un cuerpo JSON y el código indicado.
function json(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

// Lee el cuerpo completo de la petición como texto.
async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

// Atiende el feed KEV y las rutas de OSV que usa la API, con Log4Shell como única vulnerabilidad.
const server = createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/kev.json") {
    return json(res, 200, KEV_FEED);
  }
  if (req.method === "POST" && req.url === "/v1/querybatch") {
    const { queries = [] } = JSON.parse((await readBody(req)) || "{}");
    const results = queries.map((q) => (q?.package?.purl === LOG4J ? { vulns: [{ id: LOG4SHELL.id, modified: MODIFIED }] } : {}));
    return json(res, 200, { results });
  }
  if (req.method === "GET" && req.url === `/v1/vulns/${LOG4SHELL.id}`) {
    return json(res, 200, LOG4SHELL);
  }
  if (req.method === "GET" && req.url?.startsWith("/v1/vulns/")) {
    return json(res, 404, { message: "Not found" });
  }
  return json(res, 404, { message: `No fake for ${req.method} ${req.url}` });
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`fake OSV + KEV listening on http://127.0.0.1:${PORT}`);
});
