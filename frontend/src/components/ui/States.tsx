import { HttpError } from '@/api/client'

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 py-10 text-[13px] text-ink/50" role="status">
      <span className="h-2 w-2 animate-pulse rounded-full bg-forest" />
      {label}
    </div>
  )
}

export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  const offline = !(error instanceof HttpError)
  return (
    <div className="card max-w-[520px] p-6">
      <div className="text-[14px] font-semibold">{offline ? "Can't reach SavingBuddy's API" : 'Something went wrong'}</div>
      <div className="mt-1.5 text-[12.5px] leading-[1.55] text-ink/55">
        {offline
          ? 'Start the backend (cd backend && ./mvnw spring-boot:run) and try again.'
          : (error as Error).message}
      </div>
      {retry && (
        <button type="button" onClick={retry} className="mt-4 text-[12.5px] font-semibold text-forest hover:underline">
          Try again
        </button>
      )}
    </div>
  )
}
