export type BadgeTok = { name: string; emojiId: string };

export type BadgeRef =
  | { kind: "hash"; id: string }
  | { kind: "emoji"; id: string };

const HASH: Record<string, string> = {
  staff: "d173caea4aab1a4e93f35c4e16a5e29a",
  discord_employee: "d173caea4aab1a4e93f35c4e16a5e29a",
  partner: "3f9748e53446a137a052f3454e2de41e",
  partnered_server_owner: "3f9748e53446a137a052f3454e2de41e",
  hypesquad: "bf01d1073931f921909045f3a39fd264",
  hypesquad_events: "bf01d1073931f921909045f3a39fd264",
  bug_hunter: "2717692c7dca7289b35297368a940dd0",
  bughunter: "2717692c7dca7289b35297368a940dd0",
  bug_hunter2: "848f79194d4be5ff5f81505cbd0ce1e6",
  bughuntergold: "848f79194d4be5ff5f81505cbd0ce1e6",
  early: "7060786766c9c840eb3019e725d2b358",
  early_supporter: "7060786766c9c840eb3019e725d2b358",
  early_verified_bot_developer: "6df5892e0f35b051f8b61eace34f4967",
  verified_dev: "6df5892e0f35b051f8b61eace34f4967",
  moderator: "fee1624003e2fee35cb398e125dc479b",
  moderatorprogramsalumni: "fee1624003e2fee35cb398e125dc479b",
  nitro: "2ba85e8026a8614b640c2837bcdfe21b",
  discord_nitro: "2ba85e8026a8614b640c2837bcdfe21b",
  nitro_diamond: "9fd7d6c4e0b5174d1f16a21e7e0e6726",
  "24_months": "9fd7d6c4e0b5174d1f16a21e7e0e6726",
  diamond: "9fd7d6c4e0b5174d1f16a21e7e0e6726",
  booster: "51040c70d4f20a921ad6674ff86fc95c",
  boost1month: "51040c70d4f20a921ad6674ff86fc95c",
  "2monthsboostnitro": "51040c70d4f20a921ad6674ff86fc95c",
  nitro_boost_3_months: "51040c70d4f20a921ad6674ff86fc95c",
  "6months_boost": "51040c70d4f20a921ad6674ff86fc95c",
  nitro_boost_9_months: "51040c70d4f20a921ad6674ff86fc95c",
  "12monthsboostnitro": "51040c70d4f20a921ad6674ff86fc95c",
  boost15month: "51040c70d4f20a921ad6674ff86fc95c",
  nitro_boost_18_months: "51040c70d4f20a921ad6674ff86fc95c",
};

const TIER: Record<string, string> = {
  bronze: "1387742468727898182",
  silver: "1387742580300582974",
  gold: "1387742520733204480",
  platinum: "1387742556649164922",
  diamond: "1387742491629060156",
  emerald: "1387742518153707570",
  ruby: "1387742559970922496",
  opal: "1387742550919614496",
  boost1month: "1387742464202379324",
  "2monthsboostnitro": "1387742437723602975",
  nitro_boost_3_months: "1387742527339102338",
  "6months_boost": "1387742439477088287",
  nitro_boost_9_months: "1387742529289457674",
  "12monthsboostnitro": "1387742435769061417",
  boost15month: "1387742462629511270",
  nitro_boost_18_months: "1387742525699260538",
  "24_months": "1387742436742139974",
  discord_nitro: "1387742494610952194",
};

const EMOJI_TO_KEY: Record<string, string> = {
  "1387742493046734979": "discord_employee",
  "1387742553394253834": "partnered_server_owner",
  "1387742522545279056": "hypesquad_events",
  "1387742487690612887": "bughunter",
  "1387742489338970123": "bughuntergold",
  "1387742496796315779": "early_supporter",
  "1387742498226573342": "early_verified_bot_developer",
  "1537140990958243912": "botdev",
  "1528737728894734548": "nitroclassic",
  "1387742524105429032": "moderatorprogramsalumni",
  "1387742440697368606": "active_developer",
  "1387742464202379324": "boost1month",
  "1387742437723602975": "2monthsboostnitro",
  "1387742527339102338": "nitro_boost_3_months",
  "1387742439477088287": "6months_boost",
  "1387742529289457674": "nitro_boost_9_months",
  "1387742435769061417": "12monthsboostnitro",
  "1387742462629511270": "boost15month",
  "1387742525699260538": "nitro_boost_18_months",
  "1387742436742139974": "24_months",
  "1387742494610952194": "discord_nitro",
  "1387742468727898182": "bronze",
  "1387742580300582974": "silver",
  "1387742520733204480": "gold",
  "1387742556649164922": "platinum",
  "1387742491629060156": "diamond",
  "1387742518153707570": "emerald",
  "1387742559970922496": "ruby",
  "1387742550919614496": "opal",
};

const ALIAS: Record<string, string> = {
  larp_partner: "partner",
  larp_hypesquadevents: "hypesquad",
  larp_bughunter1: "bug_hunter",
  larp_bughunter2: "bug_hunter2",
  larp_early: "early",
  botdev: "botdev",
  nitroclassic: "nitroclassic",
  larp_modbadge: "moderator",
  larp_leaf: "active_developer",
  larp_bronznitro: "bronze",
  larp_silvernitro: "silver",
  larp_goldnitro: "gold",
  larp_platinnitro: "platinum",
  larp_diamondnitro: "diamond",
  larp_emeraldnitro: "emerald",
  larp_rubynitro: "ruby",
  larp_opalnitro: "opal",
  larp_boost1: "boost1month",
  larp_boost2: "2monthsboostnitro",
  larp_boost3: "nitro_boost_3_months",
  larp_boost6: "6months_boost",
  larp_boost12: "12monthsboostnitro",
  larp_boost15: "boost15month",
  larp_boost18: "nitro_boost_18_months",
  larp_boost24: "24_months",
  larp_quest: "quest",
};

const KEY_EMOJI: Record<string, string> = {
  active_developer: "1387742440697368606",
  botdev: "1537140990958243912",
  nitroclassic: "1528737728894734548",
};

export function parseBadgeTokens(raw: string | null | undefined): BadgeTok[] {
  if (!raw) return [];
  const out: BadgeTok[] = [];
  const re = /<:([^:>]+):(\d+)>/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(raw))) {
    out.push({ name: m[1], emojiId: m[2] });
  }
  return out;
}

export function resolveBadge(name?: string, emojiId?: string): BadgeRef | null {
  const keyFromEmoji = emojiId ? EMOJI_TO_KEY[emojiId] : undefined;
  const keyFromName = name ? ALIAS[name] || name : undefined;
  const key = keyFromEmoji || keyFromName;

  if (key && TIER[key]) return { kind: "emoji", id: TIER[key] };
  if (key && HASH[key]) return { kind: "hash", id: HASH[key] };
  if (key && KEY_EMOJI[key]) return { kind: "emoji", id: KEY_EMOJI[key] };
  if (emojiId && /^\d{15,25}$/.test(emojiId)) return { kind: "emoji", id: emojiId };
  return null;
}

export function badgeUrl(tok: BadgeTok): string | null {
  const ref = resolveBadge(tok.name, tok.emojiId);
  if (!ref) return null;
  if (ref.kind === "hash") {
    const key = Object.entries(HASH).find(([, h]) => h === ref.id)?.[0];
    if (key) return `/api/badges?id=${encodeURIComponent(key)}`;
    return `/api/badges?hash=${encodeURIComponent(ref.id)}`;
  }
  return `/api/badges?emoji_id=${encodeURIComponent(ref.id)}`;
}

export function hashOf(id: string): string | null {
  return HASH[id] || null;
}

export function tierEmojiOf(id: string): string | null {
  return TIER[id] || KEY_EMOJI[id] || null;
}

export function keyFromEmojiId(emojiId: string): string | null {
  return EMOJI_TO_KEY[emojiId] || null;
}
