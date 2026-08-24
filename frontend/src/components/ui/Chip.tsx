interface Props {
  label: string
  selected: boolean
  onClick: () => void
}

export function Chip({ label, selected, onClick }: Props) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={`rounded-full border px-[15px] py-[9px] text-[12.5px] transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-forest/50 ${
        selected ? 'border-ink bg-ink font-semibold text-mint' : 'border-ink/9 bg-paper font-medium text-ink/60 hover:bg-haze'
      }`}
    >
      {label}
    </button>
  )
}
