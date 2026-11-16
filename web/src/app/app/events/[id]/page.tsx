import { EventDetail } from "@/components/reporting/event-detail";

// Página de un evento reportable con sus tres reportes.
export default async function EventPage({ params }: PageProps<"/app/events/[id]">) {
  const { id } = await params;
  return <EventDetail eventId={id} />;
}
