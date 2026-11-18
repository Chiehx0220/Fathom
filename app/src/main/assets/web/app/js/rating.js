// Return YouTube Dislike counts on the rate buttons; the phone's switch decides.
FT.dislikeCounts = (info, like, dislike) => FT.api.dislikes({ id: info.url }).then((counts) => {
    if (!counts.enabled) return;
    if (counts.dislikes != null) dislike.append(FT.h('span', { class: 'count' }, FT.count(counts.dislikes)));
    if (!(info.likeCount > 0) && counts.likes > 0) like.replaceChildren(FT.icon('thumb_up'), FT.count(counts.likes));
}).catch(() => {});
