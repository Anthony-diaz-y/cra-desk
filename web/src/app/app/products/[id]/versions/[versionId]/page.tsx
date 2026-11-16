import { VersionDetail } from "@/components/products/version-detail";

// Página de una versión con su SBOM y sus vulnerabilidades.
export default async function VersionPage({ params }: PageProps<"/app/products/[id]/versions/[versionId]">) {
  const { id, versionId } = await params;
  return <VersionDetail productId={id} versionId={versionId} />;
}
