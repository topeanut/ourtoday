type P = { className?: string };
const base = "h-6 w-6";
const svg = (className: string | undefined, path: React.ReactNode, fill = false) => (
  <svg
    viewBox="0 0 24 24"
    className={className ?? base}
    fill={fill ? "currentColor" : "none"}
    stroke={fill ? "none" : "currentColor"}
    strokeWidth={1.8}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden
  >
    {path}
  </svg>
);

export const HomeIcon = ({ className }: P) =>
  svg(className, <path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" />);
export const BucketIcon = ({ className }: P) =>
  svg(className, <><path d="M9 11l3 3 8-8" /><path d="M20 12v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h9" /></>);
export const CalendarIcon = ({ className }: P) =>
  svg(className, <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 10h18" /></>);
export const UserIcon = ({ className }: P) =>
  svg(className, <><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 4-6 8-6s8 2 8 6" /></>);
export const PlusIcon = ({ className }: P) => svg(className, <path d="M12 5v14M5 12h14" />);
export const ShareIcon = ({ className }: P) =>
  svg(className, <><path d="M12 3v13" /><path d="m7 8 5-5 5 5" /><path d="M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5" /></>);
export const MoreIcon = ({ className }: P) =>
  svg(className, <><circle cx="5" cy="12" r="1.4" /><circle cx="12" cy="12" r="1.4" /><circle cx="19" cy="12" r="1.4" /></>, true);
export const BackIcon = ({ className }: P) => svg(className, <path d="M15 18l-6-6 6-6" />);
export const ChevronLeft = BackIcon;
export const ChevronRight = ({ className }: P) => svg(className, <path d="M9 18l6-6-6-6" />);
export const CloseIcon = ({ className }: P) => svg(className, <path d="M18 6 6 18M6 6l12 12" />);
export const ImageIcon = ({ className }: P) =>
  svg(className, <><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" /><path d="m21 15-5-5L5 21" /></>);
export const CheckIcon = ({ className }: P) => svg(className, <path d="M5 12l5 5L20 7" />);
export const SparkIcon = ({ className }: P) =>
  svg(className, <path d="M12 3l1.9 5.8L20 10l-6.1 1.2L12 17l-1.9-5.8L4 10l6.1-1.2z" />, true);
export const EditIcon = ({ className }: P) =>
  svg(className, <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" /></>);
export const TrashIcon = ({ className }: P) =>
  svg(className, <><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M19 6l-1 14H6L5 6" /></>);
export const KakaoSymbol = ({ className }: P) => (
  <svg viewBox="0 0 24 24" className={className ?? "h-5 w-5"} aria-hidden>
    <path
      fill="currentColor"
      d="M12 3C6.48 3 2 6.48 2 10.78c0 2.77 1.86 5.2 4.66 6.58-.2.73-.74 2.66-.85 3.07-.13.51.19.5.4.37.16-.11 2.6-1.77 3.66-2.49.7.1 1.41.15 2.13.15 5.52 0 10-3.48 10-7.78S17.52 3 12 3z"
    />
  </svg>
);
