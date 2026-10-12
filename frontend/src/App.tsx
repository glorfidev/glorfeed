import { useEffect, useRef, useState } from "react";
import type { CSSProperties, FormEvent } from "react";
import { initialStories, initialTopics } from "./mockData";
import type { Story, Topic } from "./mockData";

type View = "overview" | "topics" | "saved";
type FeedFilter = "all" | "unread" | "saved";

function viewFromHash(): View {
  const hash = window.location.hash.slice(1);
  return hash === "topics" || hash === "saved" ? hash : "overview";
}

function Icon({ name }: { name: string }) {
  return <svg aria-hidden="true"><use href={`#icon-${name}`} /></svg>;
}

function IconLibrary() {
  return (
    <svg className="icon-library" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <symbol id="icon-layers" viewBox="0 0 24 24"><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5M3 16l9 5 9-5" /></symbol>
      <symbol id="icon-bookmark" viewBox="0 0 24 24"><path d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-4-6 4V4.5Z" /></symbol>
      <symbol id="icon-search" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.5 4.5" /></symbol>
      <symbol id="icon-plus" viewBox="0 0 24 24"><path d="M12 4v16M4 12h16" /></symbol>
      <symbol id="icon-arrow" viewBox="0 0 24 24"><path d="M4 12h15m-6-6 6 6-6 6" /></symbol>
      <symbol id="icon-external" viewBox="0 0 24 24"><path d="M13 5h6v6M19 5l-9 9" /><path d="M19 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h4" /></symbol>
      <symbol id="icon-close" viewBox="0 0 24 24"><path d="M5 5 19 19M19 5 5 19" /></symbol>
    </svg>
  );
}

function topicStyle(topic: Topic): CSSProperties {
  return { "--topic-color": topic.color, "--topic-bg": topic.background } as CSSProperties;
}

function storyCount(count: number): string {
  return `${count} sample ${count === 1 ? "story" : "stories"}`;
}

function StoryCard({ story, topic, onSave, onOpen }: {
  story: Story;
  topic: Topic;
  onSave: (id: string) => void;
  onOpen: (id: string) => void;
}) {
  return (
    <article className={`story-card${story.read ? " is-read" : ""}`}>
      <div className="story-meta">
        <span className="story-topic"><span className="topic-dot" style={topicStyle(topic)} />{topic.name}</span>
        <span className="story-time">{story.time}</span>
      </div>
      <h3>{story.title}</h3>
      <p>{story.summary}</p>
      <div className="story-card-footer">
        <span className="story-source"><Icon name="external" /> {story.sources.length} sample {story.sources.length === 1 ? "source" : "sources"}</span>
        <div className="story-card-actions">
          <button className={`save-button${story.saved ? " is-saved" : ""}`} type="button" onClick={() => onSave(story.id)} aria-label={story.saved ? "Remove saved story" : "Save story"} aria-pressed={story.saved}><Icon name="bookmark" /></button>
          <button className="read-button" type="button" onClick={() => onOpen(story.id)}>Read brief <Icon name="arrow" /></button>
        </div>
      </div>
    </article>
  );
}

function EmptyState({ title, message, icon = "search" }: { title: string; message: string; icon?: string }) {
  return <div className="empty-state"><span className="empty-state-icon"><Icon name={icon} /></span><h3>{title}</h3><p>{message}</p></div>;
}

export default function App() {
  const [view, setView] = useState<View>(viewFromHash);
  const [filter, setFilter] = useState<FeedFilter>("all");
  const [search, setSearch] = useState("");
  const [topics, setTopics] = useState(initialTopics);
  const [stories, setStories] = useState(initialStories);
  const [selectedStoryId, setSelectedStoryId] = useState<string | null>(null);
  const [topicDialogOpen, setTopicDialogOpen] = useState(false);
  const [toast, setToast] = useState("");
  const topicDialog = useRef<HTMLDialogElement>(null);
  const storyDialog = useRef<HTMLDialogElement>(null);
  const topicName = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onHashChange = () => setView(viewFromHash());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    const dialog = topicDialog.current;
    if (!dialog) return;
    if (topicDialogOpen && !dialog.open) {
      dialog.showModal();
      topicName.current?.focus();
    } else if (!topicDialogOpen && dialog.open) {
      dialog.close();
    }
  }, [topicDialogOpen]);

  useEffect(() => {
    const dialog = storyDialog.current;
    if (!dialog) return;
    if (selectedStoryId && !dialog.open) dialog.showModal();
    else if (!selectedStoryId && dialog.open) dialog.close();
  }, [selectedStoryId]);

  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(""), 2800);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const navigate = (destination: View) => {
    setView(destination);
    window.history.replaceState(null, "", `#${destination}`);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const topicFor = (story: Story): Topic => {
    const topic = topics.find((candidate) => candidate.id === story.topicId);
    if (!topic) throw new Error(`Missing topic for story: ${story.id}`);
    return topic;
  };

  const saveStory = (id: string) => {
    const story = stories.find((item) => item.id === id);
    if (!story) return;
    setStories((current) => current.map((item) => item.id === id ? { ...item, saved: !item.saved } : item));
    setToast(story.saved ? "Story removed from saved" : "Story added to saved");
  };

  const openStory = (id: string) => {
    setStories((current) => current.map((item) => item.id === id ? { ...item, read: true } : item));
    setSelectedStoryId(id);
  };

  const addTopic = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const name = String(values.get("topicName") ?? "").trim();
    const description = String(values.get("topicDescription") ?? "").trim();
    if (!name) return;
    const palettes = [
      { color: "#bd7aaf", background: "#faecf6" },
      { color: "#cb9651", background: "#fff3e1" },
      { color: "#5b9fce", background: "#e9f5fc" },
    ];
    setTopics((current) => [...current, { id: crypto.randomUUID(), name, description, ...palettes[current.length % palettes.length] }]);
    form.reset();
    setTopicDialogOpen(false);
    navigate("topics");
    setToast("Topic added to this preview");
  };

  const visibleStories = stories.filter((story) => {
    const matchesFilter = filter === "all" || (filter === "saved" ? story.saved : !story.read);
    const text = `${story.title} ${story.summary} ${topicFor(story).name}`.toLowerCase();
    return matchesFilter && text.includes(search.trim().toLowerCase());
  });
  const savedStories = stories.filter((story) => story.saved);
  const selectedStory = stories.find((story) => story.id === selectedStoryId);
  const today = new Intl.DateTimeFormat("en", { month: "long", day: "numeric" }).format(new Date()).toUpperCase();
  const renderStory = (story: Story) => <StoryCard key={story.id} story={story} topic={topicFor(story)} onSave={saveStory} onOpen={openStory} />;

  return (
    <>
      <IconLibrary />
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="site-header">
        <div className="container header-inner">
          <a className="brand" href="#overview" onClick={() => navigate("overview")} aria-label="Glorfeed home">
            <img className="brand-icon" src="/brand/glorfeed-icon.png" alt="" />
            <span className="brand-lockup"><span className="brand-name">Glorfeed</span><span className="brand-subtitle">DISCOVER WHAT MATTERS</span></span>
          </a>
          <nav className="main-nav" aria-label="Main navigation">
            <button className={`nav-link${view === "overview" ? " is-active" : ""}`} type="button" onClick={() => navigate("overview")} aria-current={view === "overview" ? "page" : undefined}>Feed</button>
            <button className={`nav-link${view === "topics" ? " is-active" : ""}`} type="button" onClick={() => navigate("topics")} aria-current={view === "topics" ? "page" : undefined}>Topics <span className="nav-count">{topics.length}</span></button>
            <button className={`nav-link${view === "saved" ? " is-active" : ""}`} type="button" onClick={() => navigate("saved")} aria-current={view === "saved" ? "page" : undefined}>Saved</button>
          </nav>
          <div className="header-end"><span className="preview-label">DESIGN PREVIEW</span><span className="avatar" aria-label="Mario's preview workspace">M</span></div>
        </div>
      </header>

      <main id="main-content" className="container main-content">
        {view === "overview" && <section className="page-view" aria-labelledby="overview-title">
          <div className="page-heading">
            <div><p className="eyebrow">YOUR BRIEFING <span aria-hidden="true">✦</span> {today}</p><h1 id="overview-title">Follow what matters.</h1><p className="page-description">Fresh perspectives on the topics you choose, gathered in one quiet place.</p></div>
            <button type="button" className="primary-button" onClick={() => setTopicDialogOpen(true)}><Icon name="plus" /> Add topic</button>
          </div>
          <div className="content-grid">
            <section className="feed-section" aria-labelledby="feed-title">
              <div className="section-heading"><div><p className="section-kicker">LATEST RESEARCH</p><h2 id="feed-title">Your feed</h2></div><span className="section-count">{visibleStories.length} {visibleStories.length === 1 ? "story" : "stories"}</span></div>
              <div className="feed-toolbar">
                <div className="filter-tabs" role="group" aria-label="Filter stories">
                  {(["all", "unread", "saved"] as const).map((value) => <button key={value} className={`filter-tab${filter === value ? " is-active" : ""}`} type="button" onClick={() => setFilter(value)} aria-pressed={filter === value}>{value[0].toUpperCase() + value.slice(1)}</button>)}
                </div>
                <label className="search-field"><Icon name="search" /><span className="sr-only">Search stories</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search stories" autoComplete="off" /></label>
              </div>
              <div className="feed-list">{visibleStories.length ? visibleStories.map(renderStory) : <EmptyState title={search ? "No matching stories" : "Nothing here yet"} message={search ? "Try another search term." : filter === "saved" ? "Save a story to find it here." : "You have read all the sample stories."} />}</div>
            </section>
            <aside className="topics-panel" aria-labelledby="topics-panel-title">
              <div className="panel-heading"><div><p className="section-kicker">KEEP IN FOCUS</p><h2 id="topics-panel-title">Following</h2></div><button className="icon-button" type="button" onClick={() => setTopicDialogOpen(true)} aria-label="Add a topic"><Icon name="plus" /></button></div>
              <div className="topic-mini-list">{topics.slice(0, 4).map((topic) => <div className="topic-mini" key={topic.id}><span className="topic-mini-icon" style={topicStyle(topic)}>{topic.name[0].toUpperCase()}</span><span><strong>{topic.name}</strong><small>{storyCount(stories.filter((story) => story.topicId === topic.id).length)}</small></span></div>)}</div>
              <button className="panel-footer-button" type="button" onClick={() => navigate("topics")}>See all topics <Icon name="arrow" /></button>
            </aside>
          </div>
        </section>}

        {view === "topics" && <section className="page-view" aria-labelledby="topics-title">
          <div className="page-heading"><div><p className="eyebrow">YOUR INTERESTS</p><h1 id="topics-title">Topics you follow.</h1><p className="page-description">Each topic gives your research a direction. These are sample topics for the preview.</p></div><button type="button" className="primary-button" onClick={() => setTopicDialogOpen(true)}><Icon name="plus" /> Add topic</button></div>
          <div className="topic-grid">{topics.map((topic) => <article className="topic-card" key={topic.id}><span className="topic-card-icon" style={topicStyle(topic)}>{topic.name[0].toUpperCase()}</span><h2>{topic.name}</h2><p>{topic.description || "A topic you want to follow."}</p><div className="topic-card-footer"><Icon name="layers" /> {storyCount(stories.filter((story) => story.topicId === topic.id).length)}</div></article>)}</div>
        </section>}

        {view === "saved" && <section className="page-view" aria-labelledby="saved-title">
          <div className="page-heading"><div><p className="eyebrow">YOUR READING LIST</p><h1 id="saved-title">Stories to keep.</h1><p className="page-description">A home for the sample stories you bookmarked.</p></div></div>
          <div className="feed-list saved-list">{savedStories.length ? savedStories.map(renderStory) : <EmptyState title="Your reading list starts here" message="Save a story from the overview and it will appear here." icon="bookmark" />}</div>
        </section>}
      </main>

      <dialog className="topic-dialog" ref={topicDialog} aria-labelledby="topic-dialog-title" onClose={() => setTopicDialogOpen(false)}>
        <form onSubmit={addTopic}>
          <div className="dialog-top"><div><p className="section-kicker">FOLLOW SOMETHING NEW</p><h2 id="topic-dialog-title">Add a topic</h2></div><button type="button" className="icon-button" onClick={() => setTopicDialogOpen(false)} aria-label="Close"><Icon name="close" /></button></div>
          <p className="dialog-description">What would you like to keep up with? Changes in this preview reset when you reload the page.</p>
          <label className="form-label" htmlFor="topic-name">Topic name</label><input className="form-input" id="topic-name" ref={topicName} name="topicName" maxLength={60} placeholder="e.g. Sustainable aviation" required />
          <label className="form-label" htmlFor="topic-description">What interests you about it?</label><textarea className="form-input" id="topic-description" name="topicDescription" maxLength={200} rows={3} placeholder="The angle you'd like to follow..." />
          <div className="dialog-actions"><button className="secondary-button" type="button" onClick={() => setTopicDialogOpen(false)}>Cancel</button><button className="primary-button" type="submit">Add topic <Icon name="arrow" /></button></div>
        </form>
      </dialog>

      <dialog className="story-dialog" ref={storyDialog} aria-labelledby="story-dialog-title" onClose={() => setSelectedStoryId(null)}>
        <div className="story-dialog-inner">
          <div className="story-dialog-top"><span className="story-preview-label">SAMPLE RESEARCH BRIEF</span><button type="button" className="icon-button" onClick={() => setSelectedStoryId(null)} aria-label="Close story"><Icon name="close" /></button></div>
          {selectedStory && <><p className="story-dialog-meta">{topicFor(selectedStory).name} · {selectedStory.time}</p><h2 id="story-dialog-title">{selectedStory.title}</h2><p className="story-dialog-summary">{selectedStory.summary}</p><div className="story-divider" /><h3>The brief</h3>{selectedStory.detail.map((paragraph) => <p className="story-detail-text" key={paragraph}>{paragraph}</p>)}<div className="story-divider" /><h3>Sample source</h3><div className="source-list">{selectedStory.sources.map((source) => <a className="source-pill" href={source.url} target="_blank" rel="noopener noreferrer" key={source.url}>{source.title} <Icon name="external" /></a>)}</div><div className="story-dialog-actions"><button className="secondary-button" type="button" onClick={() => saveStory(selectedStory.id)}>{selectedStory.saved ? "Remove from saved" : "Save this story"}</button></div></>}
        </div>
      </dialog>
      <div className={`toast${toast ? " is-visible" : ""}`} role="status" aria-live="polite">{toast}</div>
    </>
  );
}
