export interface Topic {
  id: string;
  name: string;
  description: string;
  color: string;
  background: string;
}

export interface Source {
  title: string;
  url: string;
}

export interface Story {
  id: string;
  topicId: string;
  title: string;
  summary: string;
  detail: string[];
  time: string;
  sources: Source[];
  saved: boolean;
  read: boolean;
}

export const initialTopics: Topic[] = [
  { id: "ai", name: "AI & society", description: "How AI tools change work, creativity, and everyday life.", color: "#7562db", background: "#f0edff" },
  { id: "climate", name: "Climate & energy", description: "New ideas for cleaner power and a more resilient world.", color: "#43a883", background: "#e9f8f1" },
  { id: "design", name: "Product design", description: "Thoughtful interfaces and the people who use them.", color: "#e89a67", background: "#fff1e7" },
  { id: "dev", name: "Developer tooling", description: "Practical tools and patterns for building better software.", color: "#5b9fce", background: "#e9f5fc" },
];

export const initialStories: Story[] = [
  {
    id: "agents",
    topicId: "ai",
    title: "The next chapter of AI agents is about useful workflows",
    summary: "A look at how tool use, clear goals, and human review fit together when an assistant takes on more than a single prompt.",
    detail: [
      "This sample brief explores an important design question: when does an AI assistant become an agent? A useful distinction is whether it can carry out steps toward a goal, use tools, and react to what those tools return.",
      "For a real Glorfeed report, this space would contain the research-service response, citations, and a clear explanation of what changed since the previous brief.",
    ],
    time: "2 hours ago",
    sources: [{ title: "OpenAI platform documentation", url: "https://platform.openai.com/docs/" }],
    saved: false,
    read: false,
  },
  {
    id: "storage",
    topicId: "climate",
    title: "What long-duration energy storage could unlock",
    summary: "Storage is one piece of a flexible power system. This brief introduces the questions to watch as the technology develops.",
    detail: [
      "Long-duration storage is a broad category, covering different ways to move energy from a time of surplus to a time of need. The right option depends on duration, geography, cost, and the surrounding grid.",
      "This is illustrative content for the frontend preview. In the finished product, each claim should be linked to the sources gathered by the research service.",
    ],
    time: "5 hours ago",
    sources: [{ title: "IEA energy system", url: "https://www.iea.org/energy-system" }],
    saved: false,
    read: false,
  },
  {
    id: "interfaces",
    topicId: "design",
    title: "Making complex dashboards feel effortless",
    summary: "Hierarchy, whitespace, and useful defaults can help people find the story in a screen full of information.",
    detail: [
      "A dashboard is most helpful when it gives the important question a clear place to live. Grouping related information and reducing competing visual signals can make the next action easier to find.",
      "Glorfeed's own interface is a design preview of that idea. The cards and counts on this page are examples, not live research results.",
    ],
    time: "Yesterday",
    sources: [{ title: "Nielsen Norman Group", url: "https://www.nngroup.com/articles/" }],
    saved: false,
    read: false,
  },
  {
    id: "replication",
    topicId: "dev",
    title: "PostgreSQL logical replication, in plain language",
    summary: "A simple way to understand publications, replication slots, and how database changes can become events.",
    detail: [
      "A publication describes which database changes are available to subscribers. A replication slot tracks how far a consumer has read so PostgreSQL can retain changes it still needs.",
      "This sample is especially close to Glorfeed's architecture, where an outbox table and Debezium can move committed events into Kafka.",
    ],
    time: "Yesterday",
    sources: [{ title: "PostgreSQL documentation", url: "https://www.postgresql.org/docs/current/logical-replication.html" }],
    saved: false,
    read: false,
  },
  {
    id: "containers",
    topicId: "dev",
    title: "One container image, more than one architecture",
    summary: "A quick introduction to multi-platform images and why an ARM server can pull the right variant automatically.",
    detail: [
      "A multi-platform image has a manifest that points to a separate image for each supported platform. When a server pulls the image, the container runtime selects the matching variant.",
      "The Glorfeed topic-service workflow is being prepared for both AMD64 and ARM64. This card is sample content; it does not report build status.",
    ],
    time: "2 days ago",
    sources: [{ title: "Docker documentation", url: "https://docs.docker.com/build/building/multi-platform/" }],
    saved: false,
    read: false,
  },
];
