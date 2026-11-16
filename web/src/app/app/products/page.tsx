import type { Metadata } from "next";
import { ProductsList } from "@/components/products/products-list";

export const metadata: Metadata = { title: "Products" };

// Página con la lista de productos.
export default function ProductsPage() {
  return <ProductsList />;
}
