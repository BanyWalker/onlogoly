export function searchTextParts(text: string, query: string) {
	const keyword = query.trim().toLowerCase();
	if (!keyword) return [{ text, start: 0, matched: false }];
	const normalizedText = text.toLowerCase();
	const parts: { text: string; start: number; matched: boolean }[] = [];
	let cursor = 0;
	let matchIndex = normalizedText.indexOf(keyword);
	while (matchIndex !== -1) {
		if (matchIndex > cursor) parts.push({ text: text.slice(cursor, matchIndex), start: cursor, matched: false });
		parts.push({ text: text.slice(matchIndex, matchIndex + keyword.length), start: matchIndex, matched: true });
		cursor = matchIndex + keyword.length;
		matchIndex = normalizedText.indexOf(keyword, cursor);
	}
	if (cursor < text.length) parts.push({ text: text.slice(cursor), start: cursor, matched: false });
	return parts;
}
