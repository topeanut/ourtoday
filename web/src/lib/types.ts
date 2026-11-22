export type UUID = string;

export type OnboardingStatus = "NEED_PROFILE" | "NO_COUPLE" | "PENDING_INVITE" | "CONNECTED";

export type EmojiType = "HEART" | "LAUGH" | "SURPRISE" | "SAD" | "FIRE";

export interface User {
  id: UUID;
  nickname: string;
  profileImageUrl: string | null;
  kakaoId: string;
  profileCompleted: boolean;
  createdAt: string;
  hasCouple: boolean;
}

export interface CoupleMember {
  id: UUID;
  nickname: string;
  profileImageUrl: string | null;
}

export interface Couple {
  id: UUID;
  startedAt: string;
  status: "PENDING" | "CONNECTED" | "BROKEN";
  members: CoupleMember[];
}

export interface InviteResponse {
  inviteCode: string;
  expiresAt: string;
}

export interface PostImage {
  url: string;
  imageKey: string;
  order: number;
}

export interface ReactionSummary {
  emojiType: EmojiType;
  count: number;
  reactedByMe: boolean;
}

export interface Post {
  id: UUID;
  author: { id: UUID; nickname: string; profileImageUrl: string | null };
  content: string;
  postedDate: string;
  images: PostImage[];
  reactions: ReactionSummary[];
  commentCount: number;
  recentComments: Comment[];
  createdAt: string;
  updatedAt: string;
}

export interface Comment {
  id: UUID;
  author: Post["author"];
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface Memories {
  today: string;
  memories: { year: number; posts: Post[] }[];
}

export interface CalendarMemo {
  id: UUID;
  date: string;
  title: string;
  description: string | null;
  color: string | null;
  author: { id: UUID; nickname: string };
  createdAt: string;
  updatedAt: string;
}

export interface BucketItem {
  id: UUID;
  title: string;
  description: string | null;
  targetDate: string | null;
  completed: boolean;
  completedPostId: UUID | null;
  completedAt: string | null;
  createdBy: { id: UUID; nickname: string };
  createdAt: string;
}

export interface MemberStats {
  totalPosts: number;
  thisMonthPosts: number;
  consecutiveDays: number;
  totalMemos: number;
  totalReactionsGiven: number;
}

export interface CoupleStats {
  me: MemberStats;
  partner: MemberStats;
  couple: { daysTogether: number; firstPostDate: string | null };
}

export interface Settings {
  notifyMemoryToday: boolean;
  notifyPartnerPost: boolean;
}

export interface PresignedUrl {
  uploadUrl: string;
  imageKey: string;
  expiresInSec: number;
}
