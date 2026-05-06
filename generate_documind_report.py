from PIL import Image, ImageDraw, ImageFont


PAGE_WIDTH = 1654
PAGE_HEIGHT = 2339
MARGIN_X = 110
MARGIN_Y = 110
LINE_COLOR = "#254441"
TEXT_COLOR = "#1F2D2A"
MUTED_COLOR = "#5A6B67"
ACCENT = "#1B7A6E"
PANEL = "#F4F1E8"
PANEL_ALT = "#E7F1EE"
BG = "#FCFBF7"


def load_fonts():
    try:
        title = ImageFont.truetype("arialbd.ttf", 52)
        h1 = ImageFont.truetype("arialbd.ttf", 34)
        h2 = ImageFont.truetype("arialbd.ttf", 24)
        body = ImageFont.truetype("arial.ttf", 22)
        small = ImageFont.truetype("arial.ttf", 18)
        mono = ImageFont.truetype("cour.ttf", 20)
    except OSError:
        title = ImageFont.load_default()
        h1 = ImageFont.load_default()
        h2 = ImageFont.load_default()
        body = ImageFont.load_default()
        small = ImageFont.load_default()
        mono = ImageFont.load_default()
    return {
        "title": title,
        "h1": h1,
        "h2": h2,
        "body": body,
        "small": small,
        "mono": mono,
    }


FONTS = load_fonts()


def new_page():
    return Image.new("RGB", (PAGE_WIDTH, PAGE_HEIGHT), BG)


def text_size(draw, text, font):
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def draw_wrapped(draw, text, x, y, width, font, fill=TEXT_COLOR, line_gap=10):
    words = text.split()
    lines = []
    current = ""
    for word in words:
        test = word if not current else current + " " + word
        if text_size(draw, test, font)[0] <= width:
            current = test
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)

    cursor_y = y
    line_height = text_size(draw, "Ag", font)[1] + line_gap
    for line in lines:
        draw.text((x, cursor_y), line, font=font, fill=fill)
        cursor_y += line_height
    return cursor_y


def section_header(draw, title, x, y):
    draw.text((x, y), title, font=FONTS["h1"], fill=TEXT_COLOR)
    return y + 56


def bullet_list(draw, items, x, y, width, font=None, fill=TEXT_COLOR):
    font = font or FONTS["body"]
    cursor = y
    for item in items:
        draw.ellipse((x, cursor + 10, x + 10, cursor + 20), fill=ACCENT)
        cursor = draw_wrapped(draw, item, x + 24, cursor, width - 24, font, fill=fill)
        cursor += 6
    return cursor


def box(draw, xy, fill, outline=LINE_COLOR, radius=18, width=3):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def arrow(draw, start, end, fill=ACCENT, width=6):
    draw.line([start, end], fill=fill, width=width)
    ex, ey = end
    sx, sy = start
    if abs(ex - sx) > abs(ey - sy):
        direction = 1 if ex > sx else -1
        draw.polygon(
            [(ex, ey), (ex - 20 * direction, ey - 12), (ex - 20 * direction, ey + 12)],
            fill=fill,
        )
    else:
        direction = 1 if ey > sy else -1
        draw.polygon(
            [(ex, ey), (ex - 12, ey - 20 * direction), (ex + 12, ey - 20 * direction)],
            fill=fill,
        )


def centered_text(draw, rect, text, font, fill=TEXT_COLOR):
    x1, y1, x2, y2 = rect
    tw, th = text_size(draw, text, font)
    tx = x1 + (x2 - x1 - tw) / 2
    ty = y1 + (y2 - y1 - th) / 2
    draw.text((tx, ty), text, font=font, fill=fill)


def cover_page():
    img = new_page()
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((70, 70, PAGE_WIDTH - 70, PAGE_HEIGHT - 70), radius=34, outline=ACCENT, width=5)

    draw.text((MARGIN_X, 170), "DocuMind AI", font=FONTS["title"], fill=ACCENT)
    draw.text((MARGIN_X, 255), "Project Explanation Report", font=FONTS["h1"], fill=TEXT_COLOR)

    intro = (
        "This report explains what we have built so far for DocuMind AI, a RAG-based "
        "document question-answering system using Spring Boot, Ollama, PostgreSQL with "
        "PGVector, and a React frontend."
    )
    y = draw_wrapped(draw, intro, MARGIN_X, 360, PAGE_WIDTH - 2 * MARGIN_X, FONTS["body"])

    box(draw, (MARGIN_X, 500, PAGE_WIDTH - MARGIN_X, 940), PANEL)
    draw.text((MARGIN_X + 30, 530), "Current Capability Snapshot", font=FONTS["h2"], fill=TEXT_COLOR)
    y = 590
    y = bullet_list(draw, [
        "Upload PDF documents and extract readable text.",
        "Split large documents into chunks and embed them safely.",
        "Store vectors in PGVector and retrieve relevant chunks for questions.",
        "Answer with RAG using Ollama, including source previews.",
        "Manage indexed documents through a React interface.",
        "Scope questions to one chosen PDF or search all indexed documents.",
    ], MARGIN_X + 30, y, PAGE_WIDTH - 2 * MARGIN_X - 60)

    box(draw, (MARGIN_X, 1020, PAGE_WIDTH - MARGIN_X, 1880), PANEL_ALT)
    draw.text((MARGIN_X + 30, 1050), "System Overview", font=FONTS["h2"], fill=TEXT_COLOR)

    blocks = [
        ((180, 1180, 540, 1320), "React Frontend"),
        ((650, 1180, 1010, 1320), "Spring Boot API"),
        ((1120, 1180, 1480, 1320), "Ollama"),
        ((650, 1500, 1010, 1640), "PGVector"),
    ]
    for rect, label in blocks:
        box(draw, rect, "#FFFFFF")
        centered_text(draw, rect, label, FONTS["h2"])

    arrow(draw, (540, 1250), (650, 1250))
    arrow(draw, (1010, 1250), (1120, 1250))
    arrow(draw, (830, 1320), (830, 1500))

    labels = [
        ((560, 1205), "upload, ask, list, delete"),
        ((1028, 1205), "chat + embeddings"),
        ((848, 1400), "similarity search"),
    ]
    for pos, text in labels:
        draw.text(pos, text, font=FONTS["small"], fill=MUTED_COLOR)

    draw.text((MARGIN_X, PAGE_HEIGHT - 180), "Generated in workspace for project handoff and explanation.", font=FONTS["small"], fill=MUTED_COLOR)
    return img


def architecture_page():
    img = new_page()
    draw = ImageDraw.Draw(img)
    y = section_header(draw, "Architecture And Data Flow", MARGIN_X, MARGIN_Y)

    summary = (
        "DocuMind AI is organized around a clean RAG pipeline. The frontend drives the "
        "experience, the Spring backend coordinates document ingestion and retrieval, "
        "Ollama handles embeddings and answer generation, and PGVector stores searchable vectors."
    )
    y = draw_wrapped(draw, summary, MARGIN_X, y, PAGE_WIDTH - 2 * MARGIN_X, FONTS["body"])

    box(draw, (120, 340, 1534, 1180), PANEL)
    draw.text((150, 370), "Main Runtime Flow", font=FONTS["h2"], fill=TEXT_COLOR)

    runtime = [
        ((180, 500, 430, 620), "User"),
        ((500, 500, 770, 620), "React UI"),
        ((840, 500, 1130, 620), "Spring Boot"),
        ((1200, 420, 1460, 540), "Ollama"),
        ((1200, 650, 1460, 770), "PGVector"),
    ]
    for rect, label in runtime:
        box(draw, rect, "#FFFFFF")
        centered_text(draw, rect, label, FONTS["h2"])

    arrow(draw, (430, 560), (500, 560))
    arrow(draw, (770, 560), (840, 560))
    arrow(draw, (1130, 540), (1200, 480))
    arrow(draw, (1130, 600), (1200, 710))

    notes = [
        ((455, 520), "questions + uploads"),
        ((790, 520), "JSON APIs"),
        ((1080, 455), "chat / embed"),
        ((1080, 680), "retrieve / store"),
    ]
    for pos, text in notes:
        draw.text(pos, text, font=FONTS["small"], fill=MUTED_COLOR)

    box(draw, (120, 1270, 1534, 2110), PANEL_ALT)
    draw.text((150, 1300), "Endpoints In Use", font=FONTS["h2"], fill=TEXT_COLOR)
    y = 1370
    y = bullet_list(draw, [
        "POST /documents/upload: parse a PDF, chunk it, embed each chunk, and store vectors.",
        "GET /documents: return indexed document names with chunk counts.",
        "DELETE /documents/{filename}: remove all indexed chunks for a chosen PDF.",
        "GET /ai/ask?message=...: perform similarity search and answer from retrieved context.",
        "GET /ai/ask?message=...&filename=...: the same flow but restricted to a single document.",
    ], 170, y, 1280)

    return img


def progress_page():
    img = new_page()
    draw = ImageDraw.Draw(img)
    y = section_header(draw, "What We Built", MARGIN_X, MARGIN_Y)

    left_x = MARGIN_X
    right_x = 840
    col_w = 620

    groups = [
        (left_x, 220, "Backend Foundations", [
            "Spring Boot app with Ollama and PGVector configuration.",
            "Docker Compose stack for app, Ollama, and PostgreSQL.",
            "Multistage Dockerfile so source changes rebuild into the container cleanly.",
        ]),
        (right_x, 220, "Ingestion Pipeline", [
            "PDF parsing through PDFBox via LangChain4j.",
            "Chunking added to prevent embedding context overflow.",
            "Chunk metadata now stores filename and chunk index.",
        ]),
        (left_x, 760, "RAG Answering", [
            "Similarity search over PGVector wired into ChatService.",
            "Grounded prompt built from retrieved chunks instead of plain chat.",
            "Source previews returned with each answer.",
        ]),
        (right_x, 760, "Frontend Experience", [
            "React and Vite interface built in a separate frontend workspace.",
            "Upload, chat, source preview, and indexed-document panels.",
            "Dockerized frontend running on port 5173.",
        ]),
        (left_x, 1300, "Document Management", [
            "List indexed documents with chunk counts.",
            "Delete document support from both API and UI.",
            "Automatic refresh after upload or delete.",
        ]),
        (right_x, 1300, "Search Controls", [
            "Search scope selector for all documents or one chosen PDF.",
            "Backend metadata filter applied on similarity search.",
            "Selection stays aligned with current indexed documents.",
        ]),
    ]

    for x, y_box, title, items in groups:
        box(draw, (x, y_box, x + col_w, y_box + 400), PANEL if x == left_x else PANEL_ALT)
        draw.text((x + 24, y_box + 24), title, font=FONTS["h2"], fill=TEXT_COLOR)
        bullet_list(draw, items, x + 24, y_box + 82, col_w - 48, font=FONTS["body"])

    return img


def optimization_page():
    img = new_page()
    draw = ImageDraw.Draw(img)
    y = section_header(draw, "RAG Tuning And Current Direction", MARGIN_X, MARGIN_Y)

    intro = (
        "After scoped Q and A was working, the main remaining issue became answer speed and "
        "answer discipline. A scoped request succeeded, but it took a long time and the model "
        "sometimes added extra examples. We started tuning the retrieval and prompt shape to improve that."
    )
    y = draw_wrapped(draw, intro, MARGIN_X, y, PAGE_WIDTH - 2 * MARGIN_X, FONTS["body"])

    box(draw, (120, 360, 1534, 980), PANEL)
    draw.text((150, 390), "Tuning Changes Added", font=FONTS["h2"], fill=TEXT_COLOR)
    bullet_list(draw, [
        "Lowered retrieved chunk count from 4 to 3.",
        "Raised similarity threshold slightly to reduce weaker matches.",
        "Trimmed each chunk context before sending it to the LLM.",
        "Added a total context cap so prompts stay smaller.",
        "Tightened the instruction so the model stays grounded in retrieved text.",
        "Added durationMs and scope to the API response for visibility.",
        "Frontend now shows response time per assistant message.",
    ], 170, 460, 1280)

    box(draw, (120, 1080, 1534, 1980), PANEL_ALT)
    draw.text((150, 1110), "Current Improvement Path", font=FONTS["h2"], fill=TEXT_COLOR)

    steps = [
        ((190, 1250, 470, 1380), "Question"),
        ((520, 1250, 800, 1380), "Retrieve 3 Chunks"),
        ((850, 1250, 1130, 1380), "Trim Context"),
        ((1180, 1250, 1460, 1380), "Ask Ollama"),
        ((520, 1560, 800, 1690), "Measure durationMs"),
    ]
    for rect, label in steps:
        box(draw, rect, "#FFFFFF")
        centered_text(draw, rect, label, FONTS["h2"])

    arrow(draw, (470, 1315), (520, 1315))
    arrow(draw, (800, 1315), (850, 1315))
    arrow(draw, (1130, 1315), (1180, 1315))
    arrow(draw, (1320, 1380), (660, 1560))
    draw.text((920, 1450), "response metadata", font=FONTS["small"], fill=MUTED_COLOR)

    return img


def next_steps_page():
    img = new_page()
    draw = ImageDraw.Draw(img)
    y = section_header(draw, "Recommended Next Steps", MARGIN_X, MARGIN_Y)

    top = (
        "The project is now a usable end-to-end RAG application. The next steps are mostly "
        "about polish, answer quality, and product maturity rather than basic plumbing."
    )
    y = draw_wrapped(draw, top, MARGIN_X, y, PAGE_WIDTH - 2 * MARGIN_X, FONTS["body"])

    box(draw, (120, 340, 1534, 1840), PANEL)
    draw.text((150, 370), "Priority Roadmap", font=FONTS["h2"], fill=TEXT_COLOR)

    roadmap = [
        "Finish validating the new RAG tuning path after restarting the latest containers.",
        "Consider a stronger local chat model than tinyllama if hardware allows, because model quality affects grounded answers heavily.",
        "Add chat history or saved sessions so questions survive page refreshes.",
        "Store richer metadata such as upload time, page number, and perhaps user tags.",
        "Add tests for upload, retrieval, and document management endpoints.",
        "Improve source presentation by showing page-level citations when available.",
        "Add authentication later if multiple users will manage their own document spaces.",
    ]
    bullet_list(draw, roadmap, 170, 460, 1280)

    box(draw, (120, 1900, 1534, 2160), PANEL_ALT)
    draw.text((150, 1930), "Deliverables In Workspace", font=FONTS["h2"], fill=TEXT_COLOR)
    draw_wrapped(
        draw,
        "Backend code, frontend code, Docker setup, and this explanation PDF all live in the docbot workspace. "
        "The frontend runs at http://localhost:5173 and the backend API runs at http://localhost:8080.",
        170,
        2010,
        1280,
        FONTS["body"],
    )

    return img


def create_pdf(output_path):
    pages = [
        cover_page(),
        architecture_page(),
        progress_page(),
        optimization_page(),
        next_steps_page(),
    ]

    rgb_pages = [page.convert("RGB") for page in pages]
    rgb_pages[0].save(output_path, save_all=True, append_images=rgb_pages[1:], resolution=150)


if __name__ == "__main__":
    create_pdf("DocuMind_AI_Project_Report.pdf")
