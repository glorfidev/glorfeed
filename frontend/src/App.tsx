import { useCallback, useEffect, useRef, useState } from "react";
import type { CSSProperties, FormEvent } from "react";
import { sampleStories, sampleTopics } from "./mockData";
import type { SampleTopic, Story } from "./mockData";
import { createTopic, deleteTopic, getTopics, researchTopic, updateTopic } from "./topicApi";
import type { Topic, TopicInput, TopicResearchResult } from "./topicApi";
import { AuthenticationRequiredError, useAuth } from "./auth";

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
      <symbol id="icon-edit" viewBox="0 0 24 24"><path d="m14 5 5 5M4 20l4.5-1 10.9-10.9a2.1 2.1 0 0 0-3-3L5.5 16 4 20Z" /><path d="M13 20h7" /></symbol>
      <symbol id="icon-trash" viewBox="0 0 24 24"><path d="M4 7h16M10 11v6m4-6v6M5 7l1 13h12l1-13M9 7V4h6v3" /></symbol>
    </svg>
  );
}

const topicPalette = [
  { color: "#7562db", background: "#f0edff" },
  { color: "#43a883", background: "#e9f8f1" },
  { color: "#e89a67", background: "#fff1e7" },
  { color: "#5b9fce", background: "#e9f5fc" },
];

function topicStyle(topic: Topic | SampleTopic): CSSProperties {
  if ("color" in topic) {
    return { "--topic-color": topic.color, "--topic-bg": topic.background } as CSSProperties;
  }
  const paletteIndex = Array.from(topic.id).reduce((sum, char) => sum + char.charCodeAt(0), 0) % topicPalette.length;
  const palette = topicPalette[paletteIndex];
  return { "--topic-color": palette.color, "--topic-bg": palette.background } as CSSProperties;
}

function StoryCard({ story, topic, onSave, onOpen }: {
  story: Story;
  topic?: SampleTopic;
  onSave: (id: string) => void;
  onOpen: (id: string) => void;
}) {
  return (
    <article className={`story-card${story.read ? " is-read" : ""}`}>
      <div className="story-meta">
        <span className="story-topic"><span className="topic-dot" style={topic ? topicStyle(topic) : undefined} />{topic?.name ?? "Sample topic"}</span>
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

function TopicListStatus({ loading, error, hasTopics, emptyMessage, onRetry }: {
  loading: boolean;
  error: string;
  hasTopics: boolean;
  emptyMessage: string;
  onRetry: () => void;
}) {
  if (error) {
    return <div className="topic-state topic-state-error" role="alert"><span>{error}</span><button type="button" className="text-button" onClick={onRetry}>Retry</button></div>;
  }
  if (loading) return <p className="topic-state" role="status">{hasTopics ? "Refreshing topics…" : "Loading topics…"}</p>;
  if (!hasTopics) return <p className="topic-state">{emptyMessage}</p>;
  return null;
}

function createdDate(createdAt: string): string {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) return "Date unavailable";
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(date);
}

function safeHttpUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

function ResearchReferences({ title, items }: { title: string; items: TopicResearchResult["citations"] }) {
  const safeItems = Array.isArray(items) ? items.filter((item) => item && typeof item.title === "string" && typeof item.url === "string" && safeHttpUrl(item.url)) : [];
  if (!safeItems.length) return null;
  return <section className="research-references" aria-label={title}><h3>{title}</h3><ul>{safeItems.map((item, index) => <li key={`${item.url}-${index}`}><a href={safeHttpUrl(item.url)!} target="_blank" rel="noopener noreferrer">{item.title}</a></li>)}</ul></section>;
}

export default function App() {
  const auth = useAuth();
  const [view, setView] = useState<View>(viewFromHash);
  const [filter, setFilter] = useState<FeedFilter>("all");
  const [search, setSearch] = useState("");
  const [topics, setTopics] = useState<Topic[]>([]);
  const [topicsLoading, setTopicsLoading] = useState(true);
  const [topicsLoadError, setTopicsLoadError] = useState("");
  const [topicFormError, setTopicFormError] = useState("");
  const [topicActionError, setTopicActionError] = useState("");
  const [topicMutationPending, setTopicMutationPending] = useState<string | null>(null);
  const [topicForm, setTopicForm] = useState<TopicInput>({ name: "", description: "" });
  const [editingTopic, setEditingTopic] = useState<Topic | null>(null);
  const [stories, setStories] = useState(sampleStories);
  const [selectedStoryId, setSelectedStoryId] = useState<string | null>(null);
  const [topicDialogOpen, setTopicDialogOpen] = useState(false);
  const [researchDialogTopic, setResearchDialogTopic] = useState<Topic | null>(null);
  const [researchPendingTopicId, setResearchPendingTopicId] = useState<string | null>(null);
  const [researchError, setResearchError] = useState("");
  const [researchResult, setResearchResult] = useState<TopicResearchResult | null>(null);
  const [toast, setToast] = useState("");
  const topicLoadRequest = useRef(0);
  const topicMutationRevision = useRef(0);
  const topicMutationInFlight = useRef(false);
  const topicDialog = useRef<HTMLDialogElement>(null);
  const researchDialog = useRef<HTMLDialogElement>(null);
  const researchRequestGeneration = useRef(0);
  const researchInFlight = useRef(false);
  const researchMounted = useRef(false);
  const storyDialog = useRef<HTMLDialogElement>(null);
  const topicName = useRef<HTMLInputElement>(null);

  const loadTopics = useCallback(async () => {
    const requestId = ++topicLoadRequest.current;
    const revision = topicMutationRevision.current;
    setTopicsLoading(true);
    setTopicsLoadError("");
    try {
      const result = await getTopics(auth.getAccessToken);
      if (requestId === topicLoadRequest.current && revision === topicMutationRevision.current) {
        setTopics(result);
      }
    } catch (error) {
      if (error instanceof AuthenticationRequiredError) {
        auth.expireSession();
        return;
      }
      if (requestId === topicLoadRequest.current && revision === topicMutationRevision.current) {
        setTopicsLoadError(error instanceof Error ? error.message : "Could not load topics. Please try again.");
      }
    } finally {
      if (requestId === topicLoadRequest.current) setTopicsLoading(false);
    }
  }, [auth.expireSession, auth.getAccessToken]);

  useEffect(() => {
    void loadTopics();
  }, [loadTopics]);

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
    const dialog = researchDialog.current;
    if (!dialog) return;
    if (researchDialogTopic && !dialog.open) {
      dialog.showModal();
    } else if (!researchDialogTopic && dialog.open) {
      dialog.close();
    }
  }, [researchDialogTopic]);

  useEffect(() => {
    researchMounted.current = true;
    return () => {
      researchMounted.current = false;
      researchRequestGeneration.current += 1;
    };
  }, []);

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

  const topicFor = (story: Story): SampleTopic | undefined => {
    return sampleTopics.find((candidate) => candidate.id === story.topicId);
  };

  const openNewTopic = () => {
    if (topicMutationInFlight.current) return;
    setEditingTopic(null);
    setTopicForm({ name: "", description: "" });
    setTopicFormError("");
    setTopicDialogOpen(true);
  };

  const openEditTopic = (topic: Topic) => {
    if (topicMutationInFlight.current) return;
    setEditingTopic(topic);
    setTopicForm({ name: topic.name, description: topic.description });
    setTopicFormError("");
    setTopicDialogOpen(true);
  };

  const closeTopicDialog = () => {
    if (topicMutationInFlight.current) return;
    setTopicDialogOpen(false);
    setEditingTopic(null);
    setTopicForm({ name: "", description: "" });
    setTopicFormError("");
  };

  const openResearchDialog = (topic: Topic) => {
    if (topicMutationInFlight.current || researchInFlight.current) return;
    researchRequestGeneration.current += 1;
    setResearchDialogTopic(topic);
    setResearchError("");
    setResearchResult(null);
  };

  const closeResearchDialog = () => {
    if (researchInFlight.current) return;
    researchRequestGeneration.current += 1;
    setResearchDialogTopic(null);
    setResearchError("");
    setResearchResult(null);
  };

  const runResearch = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const topic = researchDialogTopic;
    if (!topic || researchInFlight.current) return;

    const generation = ++researchRequestGeneration.current;
    const subject = auth.subject;
    researchInFlight.current = true;
    setResearchPendingTopicId(topic.id);
    setResearchError("");
    setResearchResult(null);
    try {
      const result = await researchTopic(topic.id, auth.getAccessToken);
      if (researchMounted.current && generation === researchRequestGeneration.current && auth.subject === subject) {
        setResearchResult(result);
      }
    } catch (error) {
      if (!researchMounted.current || generation !== researchRequestGeneration.current || auth.subject !== subject) return;
      if (error instanceof AuthenticationRequiredError) {
        auth.expireSession();
        return;
      }
      setResearchError(error instanceof Error ? error.message : "Research could not be completed. Check before running again.");
    } finally {
      if (generation === researchRequestGeneration.current) researchInFlight.current = false;
      if (researchMounted.current && generation === researchRequestGeneration.current) setResearchPendingTopicId(null);
    }
  };

  const beginTopicMutation = (key: string): boolean => {
    if (topicMutationInFlight.current) return false;
    topicMutationInFlight.current = true;
    topicMutationRevision.current += 1;
    setTopicMutationPending(key);
    setTopicActionError("");
    return true;
  };

  const finishTopicMutation = () => {
    topicMutationRevision.current += 1;
    topicMutationInFlight.current = false;
    setTopicMutationPending(null);
  };

  const saveTopic = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (topicMutationInFlight.current) return;
    const input = { name: topicForm.name.trim(), description: topicForm.description.trim() };
    if (!input.name || !input.description) {
      setTopicFormError("Enter both a topic name and description.");
      return;
    }
    if (input.name.length > 255 || input.description.length > 10_000) {
      setTopicFormError("The name must be 255 characters or fewer and the description 10,000 or fewer.");
      return;
    }

    const currentEditingTopic = editingTopic;
    if (!beginTopicMutation(currentEditingTopic?.id ?? "new")) return;
    let refresh = false;
    try {
      if (currentEditingTopic) {
        const updated = await updateTopic(currentEditingTopic.id, input, auth.getAccessToken);
        setTopics((current) => current.some((topic) => topic.id === updated.id)
          ? current.map((topic) => topic.id === updated.id ? updated : topic)
          : [updated, ...current]);
        setToast("Topic updated");
      } else {
        const created = await createTopic(input, auth.getAccessToken);
        setTopics((current) => current.some((topic) => topic.id === created.id)
          ? current.map((topic) => topic.id === created.id ? created : topic)
          : [created, ...current]);
        setToast("Topic added");
        navigate("topics");
      }
      setTopicDialogOpen(false);
      setEditingTopic(null);
      setTopicForm({ name: "", description: "" });
      setTopicFormError("");
      refresh = true;
    } catch (error) {
      if (error instanceof AuthenticationRequiredError) {
        auth.expireSession();
        return;
      }
      setTopicFormError(error instanceof Error ? error.message : "Could not save the topic. Please try again.");
      refresh = true;
    } finally {
      finishTopicMutation();
    }
    if (refresh) void loadTopics();
  };

  const removeTopic = async (topic: Topic) => {
    if (topicMutationInFlight.current || !window.confirm(`Delete “${topic.name}”? This cannot be undone.`)) return;
    if (!beginTopicMutation(topic.id)) return;
    let refresh = false;
    try {
      await deleteTopic(topic.id, auth.getAccessToken);
      setTopics((current) => current.filter((item) => item.id !== topic.id));
      setToast("Topic deleted");
      refresh = true;
    } catch (error) {
      if (error instanceof AuthenticationRequiredError) {
        auth.expireSession();
        return;
      }
      setTopicActionError(error instanceof Error ? error.message : "Could not delete the topic. Please try again.");
      refresh = true;
    } finally {
      finishTopicMutation();
    }
    if (refresh) void loadTopics();
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

  const visibleStories = stories.filter((story) => {
    const matchesFilter = filter === "all" || (filter === "saved" ? story.saved : !story.read);
    const text = `${story.title} ${story.summary} ${topicFor(story)?.name ?? ""}`.toLowerCase();
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
          <div className="header-end"><span className="preview-label">{auth.displayName}</span><button className="text-button auth-logout" type="button" onClick={auth.logout}>Sign out</button></div>
        </div>
      </header>

      <main id="main-content" className="container main-content">
        {view === "overview" && <section className="page-view" aria-labelledby="overview-title">
          <div className="page-heading">
            <div><p className="eyebrow">YOUR BRIEFING <span aria-hidden="true">✦</span> {today}</p><h1 id="overview-title">Follow what matters.</h1><p className="page-description">Fresh perspectives on the topics you choose, gathered in one quiet place.</p></div>
            <button type="button" className="primary-button" onClick={openNewTopic}><Icon name="plus" /> Add topic</button>
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
              <div className="panel-heading"><div><p className="section-kicker">KEEP IN FOCUS</p><h2 id="topics-panel-title">Following</h2></div><button className="icon-button" type="button" onClick={openNewTopic} aria-label="Add a topic"><Icon name="plus" /></button></div>
              <div className="topic-mini-list">
                <TopicListStatus loading={topicsLoading} error={topicsLoadError} hasTopics={topics.length > 0} emptyMessage="No live topics yet. Add one to get started." onRetry={() => void loadTopics()} />
                {topics.slice(0, 4).map((topic) => <div className="topic-mini" key={topic.id}><span className="topic-mini-icon" style={topicStyle(topic)}>{topic.name[0].toUpperCase()}</span><span><strong>{topic.name}</strong><small>Live topic</small></span></div>)}
              </div>
              <button className="panel-footer-button" type="button" onClick={() => navigate("topics")}>See all topics <Icon name="arrow" /></button>
            </aside>
          </div>
        </section>}

        {view === "topics" && <section className="page-view" aria-labelledby="topics-title">
          <div className="page-heading"><div><p className="eyebrow">YOUR INTERESTS</p><h1 id="topics-title">Topics you follow.</h1><p className="page-description">Manage your live topics. Feed stories are sample preview content and are not linked to these records.</p></div><button type="button" className="primary-button" onClick={openNewTopic}><Icon name="plus" /> Add topic</button></div>
          {topicActionError && <p className="topic-action-error" role="alert">{topicActionError}</p>}
          <TopicListStatus loading={topicsLoading} error={topicsLoadError} hasTopics={topics.length > 0} emptyMessage="You haven't added any live topics yet." onRetry={() => void loadTopics()} />
          <div className="topic-grid">
            {topics.map((topic) => <article className="topic-card" key={topic.id}>
              <div className="topic-card-top"><span className="topic-card-icon" style={topicStyle(topic)}>{topic.name[0].toUpperCase()}</span><div className="topic-card-actions">
                <button className="topic-action-button" type="button" onClick={() => openEditTopic(topic)} disabled={topicMutationPending !== null} aria-label={`Edit ${topic.name}`}><Icon name="edit" /></button>
                <button className="topic-action-button is-danger" type="button" onClick={() => void removeTopic(topic)} disabled={topicMutationPending !== null} aria-label={topicMutationPending === topic.id ? `Deleting ${topic.name}` : `Delete ${topic.name}`}><Icon name="trash" /></button>
              </div></div>
              <h2>{topic.name}</h2><p>{topic.description}</p>
              <div className="topic-card-footer"><span>Added {createdDate(topic.createdAt)}</span><button className="text-button topic-research-button" type="button" onClick={() => openResearchDialog(topic)} disabled={topicMutationPending !== null || researchPendingTopicId !== null}>Run research</button></div>
            </article>)}
          </div>
        </section>}

        {view === "saved" && <section className="page-view" aria-labelledby="saved-title">
          <div className="page-heading"><div><p className="eyebrow">YOUR READING LIST</p><h1 id="saved-title">Stories to keep.</h1><p className="page-description">A home for the sample stories you bookmarked.</p></div></div>
          <div className="feed-list saved-list">{savedStories.length ? savedStories.map(renderStory) : <EmptyState title="Your reading list starts here" message="Save a story from the overview and it will appear here." icon="bookmark" />}</div>
        </section>}
      </main>

      <dialog className="topic-dialog" ref={topicDialog} aria-labelledby="topic-dialog-title" onClose={closeTopicDialog} onCancel={(event) => {
        if (topicMutationInFlight.current) event.preventDefault();
        else closeTopicDialog();
      }}>
        <form onSubmit={saveTopic}>
          <div className="dialog-top"><div><p className="section-kicker">{editingTopic ? "REFINE YOUR INTEREST" : "FOLLOW SOMETHING NEW"}</p><h2 id="topic-dialog-title">{editingTopic ? "Edit topic" : "Add a topic"}</h2></div><button type="button" className="icon-button" onClick={closeTopicDialog} disabled={topicMutationPending !== null} aria-label="Close"><Icon name="close" /></button></div>
          <p className="dialog-description">Live topics are saved to your Glorfeed workspace. Sample feed stories remain preview content.</p>
          <label className="form-label" htmlFor="topic-name">Topic name</label><input className="form-input" id="topic-name" ref={topicName} name="topicName" value={topicForm.name} onChange={(event) => { setTopicForm((current) => ({ ...current, name: event.target.value })); setTopicFormError(""); }} maxLength={255} placeholder="e.g. Sustainable aviation" required disabled={topicMutationPending !== null} />
          <label className="form-label" htmlFor="topic-description">Description</label><textarea className="form-input" id="topic-description" name="topicDescription" value={topicForm.description} onChange={(event) => { setTopicForm((current) => ({ ...current, description: event.target.value })); setTopicFormError(""); }} maxLength={10_000} rows={3} placeholder="The angle you'd like to follow..." required disabled={topicMutationPending !== null} />
          {topicFormError && <p className="form-error" role="alert">{topicFormError}</p>}
          <div className="dialog-actions"><button className="secondary-button" type="button" onClick={closeTopicDialog} disabled={topicMutationPending !== null}>Cancel</button><button className="primary-button" type="submit" disabled={topicMutationPending !== null}>{topicMutationPending !== null ? "Saving…" : editingTopic ? "Save changes" : "Add topic"} <Icon name="arrow" /></button></div>
        </form>
      </dialog>

      <dialog className="topic-dialog research-dialog" ref={researchDialog} aria-labelledby="research-dialog-title" onClose={() => {
        if (researchDialogTopic && !researchInFlight.current) closeResearchDialog();
      }} onCancel={(event) => {
        event.preventDefault();
        if (!researchInFlight.current) closeResearchDialog();
      }}>
        <form onSubmit={runResearch}>
          <div className="dialog-top"><div><p className="section-kicker">ON-DEMAND RESEARCH</p><h2 id="research-dialog-title">Research {researchDialogTopic?.name ?? "topic"}</h2></div><button type="button" className="icon-button" onClick={closeResearchDialog} disabled={researchInFlight.current} aria-label="Close research dialog"><Icon name="close" /></button></div>
          <p className="dialog-description">This sends one paid AI web-search request for the topic. No request is sent until you choose Run research, and requests are not automatically retried.</p>
          {researchPendingTopicId && <p className="research-status" role="status">Research is running. Keep this dialog open while the service responds.</p>}
          {researchError && <p className="form-error" role="alert">{researchError}</p>}
          {researchResult && <section className="research-result" aria-label="Research report">
            <p className="research-result-meta">{researchResult.model} · {researchResult.inputTokens} input tokens · {researchResult.outputTokens} output tokens · {researchResult.webSearchCalls} web searches</p>
            <div className="research-report-text">{typeof researchResult.text === "string" ? researchResult.text : "Research response text is unavailable."}</div>
            <ResearchReferences title="Citations" items={researchResult.citations} />
            <ResearchReferences title="Sources" items={researchResult.sources} />
          </section>}
          <div className="dialog-actions"><button className="secondary-button" type="button" onClick={closeResearchDialog} disabled={researchInFlight.current}>Cancel</button><button className="primary-button" type="submit" disabled={researchPendingTopicId !== null || !researchDialogTopic}>{researchPendingTopicId ? "Researching…" : "Run research"} <Icon name="arrow" /></button></div>
        </form>
      </dialog>

      <dialog className="story-dialog" ref={storyDialog} aria-labelledby="story-dialog-title" onClose={() => setSelectedStoryId(null)}>
        <div className="story-dialog-inner">
          <div className="story-dialog-top"><span className="story-preview-label">SAMPLE RESEARCH BRIEF</span><button type="button" className="icon-button" onClick={() => setSelectedStoryId(null)} aria-label="Close story"><Icon name="close" /></button></div>
          {selectedStory && <><p className="story-dialog-meta">{topicFor(selectedStory)?.name ?? "Sample topic"} · {selectedStory.time}</p><h2 id="story-dialog-title">{selectedStory.title}</h2><p className="story-dialog-summary">{selectedStory.summary}</p><div className="story-divider" /><h3>The brief</h3>{selectedStory.detail.map((paragraph) => <p className="story-detail-text" key={paragraph}>{paragraph}</p>)}<div className="story-divider" /><h3>Sample source</h3><div className="source-list">{selectedStory.sources.map((source) => <a className="source-pill" href={source.url} target="_blank" rel="noopener noreferrer" key={source.url}>{source.title} <Icon name="external" /></a>)}</div><div className="story-dialog-actions"><button className="secondary-button" type="button" onClick={() => saveStory(selectedStory.id)}>{selectedStory.saved ? "Remove from saved" : "Save this story"}</button></div></>}
        </div>
      </dialog>
      <div className={`toast${toast ? " is-visible" : ""}`} role="status" aria-live="polite">{toast}</div>
    </>
  );
}
