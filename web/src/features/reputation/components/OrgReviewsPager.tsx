import { Pagination } from '@/components/ui/pagination';

/** Paginación SSR del listado de reseñas: navega a `?page=` (base 1). */
export default function OrgReviewsPager({ page, size, total }: { page: number; size: number; total: number }) {
  return <Pagination page={page} size={size} total={total} onPageChange={(p) => { const u = new URL(window.location.href); u.searchParams.set('page', String(p)); window.location.href = u.toString(); }} />;
}
