import { ProductDetail } from "@/components/products/product-detail";

// Página de un producto con sus versiones.
export default async function ProductPage({ params }: PageProps<"/app/products/[id]">) {
  const { id } = await params;
  return <ProductDetail productId={id} />;
}
