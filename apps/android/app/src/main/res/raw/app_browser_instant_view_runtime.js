(function() {
  function finish(result) {
    if (window.NativeDoInstantView && typeof window.NativeDoInstantView.onResult === "function") {
      window.NativeDoInstantView.onResult(result || "");
    }
  }

  function normalizeText(value) {
    return (value || "")
      .replace(/\u00a0/g, " ")
      .replace(/[ \t]+/g, " ")
      .replace(/\n{3,}/g, "\n\n")
      .trim();
  }

  function normalizeInlineText(value) {
    return (value || "")
      .replace(/\u00a0/g, " ")
      .replace(/[ \t]+/g, " ");
  }

  function getCharset(document) {
    var meta = document.querySelector("meta[charset]");
    if (meta) {
      return meta.getAttribute("charset") || "utf-8";
    }
    meta = document.querySelector('meta[http-equiv="Content-Type"]');
    if (!meta) {
      return "utf-8";
    }
    var content = meta.getAttribute("content") || "";
    var parts = content.split(/; */g);
    for (var i = 0; i < parts.length; i += 1) {
      var separatorIndex = parts[i].indexOf("=");
      if (separatorIndex < 0) {
        continue;
      }
      var name = parts[i].substring(0, separatorIndex).trim().toLowerCase();
      if (name === "charset") {
        return parts[i].substring(separatorIndex + 1).trim() || "utf-8";
      }
    }
    return "utf-8";
  }

  function sanitizeHtml(dirtyHtml) {
    return window.DOMPurify.sanitize(dirtyHtml, {
      WHOLE_DOCUMENT: true,
      ADD_TAGS: ["meta", "iframe"],
      ADD_ATTR: ["http-equiv", "content"]
    });
  }

  function queryMeta(document, selectors) {
    for (var i = 0; i < selectors.length; i += 1) {
      var node = document.querySelector(selectors[i]);
      if (!node) {
        continue;
      }
      var value = normalizeText(node.getAttribute("content") || node.innerText || node.textContent);
      if (value) {
        return value;
      }
    }
    return "";
  }

  function absoluteUrl(baseUrl, candidate) {
    var normalizedCandidate = normalizeText(candidate);
    if (!normalizedCandidate) {
      return "";
    }
    try {
      return new URL(normalizedCandidate, baseUrl).toString();
    } catch (error) {
      return normalizedCandidate;
    }
  }

  function currentLength(buffer) {
    return buffer.join("").length;
  }

  function appendText(buffer, text) {
    if (!text) {
      return;
    }
    buffer.push(text);
  }

  function applyEntity(entities, start, end, type, extra) {
    if (start >= end) {
      return;
    }
    var entity = {
      type: type,
      start: start,
      end: end
    };
    if (extra) {
      Object.keys(extra).forEach(function(key) {
        if (extra[key] !== null && typeof extra[key] !== "undefined" && extra[key] !== "") {
          entity[key] = extra[key];
        }
      });
    }
    entities.push(entity);
  }

  function appendInlineNode(node, baseUrl, buffer, entities) {
    if (!node) {
      return;
    }
    if (node.nodeType === Node.TEXT_NODE) {
      appendText(buffer, normalizeInlineText(node.textContent));
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return;
    }

    var tag = (node.tagName || "").toLowerCase();
    if (tag === "br") {
      appendText(buffer, "\n");
      return;
    }
    if (tag === "img") {
      var alt = normalizeInlineText(node.getAttribute("alt") || "");
      if (alt) {
        appendText(buffer, alt);
      }
      return;
    }

    var start = currentLength(buffer);
    Array.prototype.forEach.call(node.childNodes || [], function(child) {
      appendInlineNode(child, baseUrl, buffer, entities);
    });
    var end = currentLength(buffer);

    if (tag === "strong" || tag === "b") {
      applyEntity(entities, start, end, "bold");
      return;
    }
    if (tag === "em" || tag === "i") {
      applyEntity(entities, start, end, "italic");
      return;
    }
    if (tag === "del" || tag === "s" || tag === "strike") {
      applyEntity(entities, start, end, "strike");
      return;
    }
    if (tag === "code") {
      applyEntity(entities, start, end, "inline_code");
      return;
    }
    if (tag === "u") {
      applyEntity(entities, start, end, "underline");
      return;
    }
    if (tag === "mark") {
      applyEntity(entities, start, end, "highlight");
      return;
    }
    if (tag === "sup") {
      applyEntity(entities, start, end, "superscript");
      return;
    }
    if (tag === "sub") {
      applyEntity(entities, start, end, "subscript");
      return;
    }
    if (tag === "small") {
      applyEntity(entities, start, end, "small");
      return;
    }
    if (tag === "kbd") {
      applyEntity(entities, start, end, "kbd");
      return;
    }
    if (tag === "a") {
      var href = absoluteUrl(baseUrl, node.getAttribute("href") || "");
      if (href) {
        applyEntity(entities, start, end, "link", { url: href });
      }
    }
  }

  function buildTextContentFromNode(node, baseUrl) {
    var buffer = [];
    var entities = [];
    Array.prototype.forEach.call((node && node.childNodes) || [], function(child) {
      appendInlineNode(child, baseUrl, buffer, entities);
    });
    var rawText = buffer.join("");
    var leadingTrim = 0;
    while (leadingTrim < rawText.length && /\s/.test(rawText.charAt(leadingTrim))) {
      leadingTrim += 1;
    }
    var trailingTrim = 0;
    while (trailingTrim < rawText.length - leadingTrim && /\s/.test(rawText.charAt(rawText.length - 1 - trailingTrim))) {
      trailingTrim += 1;
    }
    var text = normalizeText(rawText);
    if (!text) {
      return null;
    }
    return {
      text: text,
      entities: entities
        .map(function(entity) {
          var start = Math.max(0, entity.start - leadingTrim);
          var end = Math.max(0, entity.end - leadingTrim);
          if (trailingTrim > 0) {
            var maxEnd = rawText.length - leadingTrim - trailingTrim;
            start = Math.min(start, maxEnd);
            end = Math.min(end, maxEnd);
          }
          var next = {
            type: entity.type,
            start: Math.min(start, text.length),
            end: Math.min(end, text.length)
          };
          if (entity.url) {
            next.url = entity.url;
          }
          if (entity.username) {
            next.username = entity.username;
          }
          if (next.start >= next.end) {
            return null;
          }
          return next;
        })
        .filter(Boolean)
    };
  }

  function paragraphBlockFromText(text) {
    var normalized = normalizeText(text);
    if (!normalized) {
      return null;
    }
    return {
      type: "paragraph",
      content: {
        text: normalized,
        entities: []
      }
    };
  }

  function paragraphBlockFromElement(element, baseUrl) {
    var content = buildTextContentFromNode(element, baseUrl);
    if (!content) {
      return null;
    }
    return {
      type: "paragraph",
      content: content
    };
  }

  function containsOnlyInlineImage(element) {
    if (!element) {
      return false;
    }
    var nonEmptyText = "";
    Array.prototype.forEach.call(element.childNodes || [], function(child) {
      if (child && child.nodeType === Node.TEXT_NODE) {
        nonEmptyText += normalizeText(child.textContent || "");
      }
    });
    if (nonEmptyText) {
      return false;
    }
    var imageCandidates = element.querySelectorAll("img, picture source, picture img");
    if (!imageCandidates.length) {
      return false;
    }
    var cloned = element.cloneNode(true);
    Array.prototype.forEach.call(cloned.querySelectorAll("img, picture, source"), function(node) {
      if (node && node.parentNode) {
        node.parentNode.removeChild(node);
      }
    });
    return !normalizeText(cloned.textContent || "");
  }

  function codeLanguageFromNode(node) {
    var className = ((node.className || "") + " " + ((node.querySelector && (node.querySelector("code") || {}).className) || "")).trim();
    var match = className.match(/language-([a-z0-9_+-]+)/i) || className.match(/lang-([a-z0-9_+-]+)/i);
    return match ? match[1].toLowerCase() : null;
  }

  function parseDimension(value) {
    var normalized = normalizeText(value);
    if (!normalized) {
      return null;
    }
    var match = normalized.match(/^\d+/);
    if (!match) {
      return null;
    }
    var parsed = parseInt(match[0], 10);
    return parsed > 0 ? parsed : null;
  }

  function bestImageUrl(node, baseUrl) {
    if (!node) {
      return "";
    }
    var candidates = [
      node.getAttribute && node.getAttribute("src"),
      node.getAttribute && node.getAttribute("data-src"),
      node.getAttribute && node.getAttribute("data-original"),
      node.getAttribute && node.getAttribute("poster")
    ];
    if (node.getAttribute) {
      var srcset = node.getAttribute("srcset") || node.getAttribute("data-srcset") || "";
      if (srcset) {
        srcset.split(",").forEach(function(part) {
          candidates.push(part.trim().split(/\s+/)[0]);
        });
      }
    }
    for (var i = 0; i < candidates.length; i += 1) {
      var resolved = absoluteUrl(baseUrl, candidates[i] || "");
      if (resolved) {
        return resolved;
      }
    }
    return "";
  }

  function splitFigureTextAndCredit(captionNode, baseUrl) {
    if (!captionNode) {
      return {
        caption: null,
        credit: null
      };
    }
    var creditNode = captionNode.querySelector(".credit, .caption-credit, [data-credit], [itemprop='copyrightNotice']");
    var credit = creditNode ? buildTextContentFromNode(creditNode, baseUrl) : null;
    if (creditNode && creditNode.parentNode) {
      creditNode.parentNode.removeChild(creditNode);
    }
    var caption = buildTextContentFromNode(captionNode, baseUrl);
    return {
      caption: caption,
      credit: credit
    };
  }

  function imageBlockFromElement(element, baseUrl, captionContent, creditContent) {
    var imageNode = element;
    var tag = (element.tagName || "").toLowerCase();
    if (tag === "figure" || tag === "picture") {
      imageNode = element.querySelector("img, source");
    }
    if (!imageNode) {
      return null;
    }
    var url = bestImageUrl(imageNode, baseUrl);
    if (!url) {
      return null;
    }
    var alt = normalizeText((imageNode.getAttribute && imageNode.getAttribute("alt")) || "");
    var width = parseDimension((imageNode.getAttribute && imageNode.getAttribute("width")) || "") || imageNode.naturalWidth || null;
    var height = parseDimension((imageNode.getAttribute && imageNode.getAttribute("height")) || "") || imageNode.naturalHeight || null;
    return {
      type: captionContent ? "figure" : "image",
      url: url,
      width: width,
      height: height,
      alt: alt || null,
      caption: captionContent || null,
      credit: creditContent || null
    };
  }

  function tableCellAlignment(element) {
    if (!element) {
      return "start";
    }
    var align = normalizeText(element.getAttribute("align") || "").toLowerCase();
    if (align === "center" || align === "middle") {
      return "center";
    }
    if (align === "right" || align === "end") {
      return "end";
    }
    var className = ((element.className || "") + "").toLowerCase();
    if (className.indexOf("align-center") !== -1) {
      return "center";
    }
    if (className.indexOf("align-right") !== -1 || className.indexOf("align-end") !== -1) {
      return "end";
    }
    return "start";
  }

  function tableBlockFromElement(element, baseUrl) {
    if (!element) {
      return null;
    }
    var titleNode = element.querySelector("caption");
    var titleContent = titleNode ? buildTextContentFromNode(titleNode, baseUrl) : null;
    var rows = [];
    Array.prototype.forEach.call(element.querySelectorAll("tr"), function(rowNode) {
      var cells = [];
      Array.prototype.forEach.call(rowNode.children || [], function(cellNode) {
        var tag = (cellNode.tagName || "").toLowerCase();
        if (tag !== "th" && tag !== "td") {
          return;
        }
        var content = buildTextContentFromNode(cellNode, baseUrl);
        if (!content) {
          return;
        }
        cells.push({
          content: content,
          header: tag === "th",
          colspan: parseDimension(cellNode.getAttribute("colspan") || "") || 1,
          rowspan: parseDimension(cellNode.getAttribute("rowspan") || "") || 1,
          align: tableCellAlignment(cellNode)
        });
      });
      if (cells.length > 0) {
        rows.push({ cells: cells });
      }
    });
    if (!rows.length) {
      return null;
    }
    return {
      type: "table",
      titleContent: titleContent,
      striped: /\bstriped\b/i.test(element.className || ""),
      bordered: !/\bborderless\b/i.test(element.className || ""),
      rows: rows
    };
  }

  function isInlineContainerTag(tag) {
    return [
      "span",
      "a",
      "strong",
      "b",
      "em",
      "i",
      "u",
      "mark",
      "small",
      "sup",
      "sub",
      "kbd",
      "code",
      "cite"
    ].indexOf(tag) !== -1;
  }

  function isBlockElement(node) {
    if (!node || node.nodeType !== Node.ELEMENT_NODE) {
      return false;
    }
    var tag = (node.tagName || "").toLowerCase();
    if (isInlineContainerTag(tag)) {
      return false;
    }
    return [
      "p",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "blockquote",
      "ul",
      "ol",
      "details",
      "pre",
      "table",
      "img",
      "picture",
      "figure",
      "hr",
      "article",
      "main",
      "section",
      "div",
      "body",
      "aside"
    ].indexOf(tag) !== -1;
  }

  function paragraphBlockFromNodes(nodes, baseUrl) {
    var wrapper = document.createElement("div");
    Array.prototype.forEach.call(nodes || [], function(node) {
      if (!node) {
        return;
      }
      wrapper.appendChild(node.cloneNode(true));
    });
    return paragraphBlockFromElement(wrapper, baseUrl);
  }

  function parseBlocks(nodes, baseUrl) {
    var blocks = [];
    var inlineBuffer = [];

    function flushInlineBuffer() {
      if (!inlineBuffer.length) {
        return;
      }
      var paragraph = paragraphBlockFromNodes(inlineBuffer, baseUrl);
      inlineBuffer = [];
      if (paragraph) {
        blocks.push(paragraph);
      }
    }

    Array.prototype.forEach.call(nodes || [], function(node) {
      if (!node) {
        return;
      }
      if (node.nodeType === Node.TEXT_NODE) {
        inlineBuffer.push(node);
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) {
        return;
      }

      if (!isBlockElement(node)) {
        inlineBuffer.push(node);
        return;
      }

      flushInlineBuffer();

      var tag = (node.tagName || "").toLowerCase();
      if (tag === "p") {
        if (containsOnlyInlineImage(node)) {
          var paragraphImage = imageBlockFromElement(node, baseUrl, null, null);
          if (paragraphImage) {
            blocks.push(paragraphImage);
            return;
          }
        }
        var paragraph = paragraphBlockFromElement(node, baseUrl);
        if (paragraph) {
          blocks.push(paragraph);
        }
        return;
      }
      if (/^h[1-6]$/.test(tag)) {
        var headingContent = buildTextContentFromNode(node, baseUrl);
        if (headingContent) {
          blocks.push({
            type: "heading",
            level: parseInt(tag.substring(1), 10) || 1,
            content: headingContent
          });
        }
        return;
      }
      if (tag === "blockquote") {
        var attributionNode = node.querySelector("cite, footer");
        var attribution = attributionNode ? buildTextContentFromNode(attributionNode, baseUrl) : null;
        if (attributionNode && attributionNode.parentNode) {
          attributionNode.parentNode.removeChild(attributionNode);
        }
        var quoteContent = buildTextContentFromNode(node, baseUrl);
        if (quoteContent) {
          blocks.push({
            type: "pullquote",
            content: quoteContent,
            attribution: attribution
          });
          return;
        }
        var quoteBlocks = parseBlocks(node.childNodes, baseUrl);
        if (quoteBlocks.length > 0) {
          blocks.push({
            type: "quote",
            blocks: quoteBlocks
          });
        }
        return;
      }
      if (tag === "ul" || tag === "ol") {
        var items = [];
        Array.prototype.forEach.call(node.children || [], function(child) {
          if ((child.tagName || "").toLowerCase() !== "li") {
            return;
          }
          var itemBlocks = parseBlocks(child.childNodes, baseUrl);
          if (itemBlocks.length === 0) {
            var fallbackParagraph = paragraphBlockFromElement(child, baseUrl);
            if (fallbackParagraph) {
              itemBlocks = [fallbackParagraph];
            }
          }
          if (itemBlocks.length > 0) {
            items.push({ blocks: itemBlocks });
          }
        });
        if (items.length > 0) {
          blocks.push({
            type: "list",
            ordered: tag === "ol",
            items: items
          });
        }
        return;
      }
      if (tag === "details") {
        var summaryNode = node.querySelector("summary");
        var detailSummary = summaryNode ? buildTextContentFromNode(summaryNode, baseUrl) : null;
        if (detailSummary) {
          var childNodes = Array.prototype.filter.call(node.childNodes || [], function(child) {
            return !(child && child.nodeType === Node.ELEMENT_NODE && (child.tagName || "").toLowerCase() === "summary");
          });
          blocks.push({
            type: "details",
            summary: detailSummary,
            expanded: node.hasAttribute("open"),
            blocks: parseBlocks(childNodes, baseUrl)
          });
        }
        return;
      }
      if (tag === "pre") {
        var codeText = normalizeText(node.innerText || node.textContent);
        if (codeText) {
          blocks.push({
            type: "code",
            code: codeText,
            language: codeLanguageFromNode(node)
          });
        }
        return;
      }
      if (tag === "hr") {
        blocks.push({ type: "divider" });
        return;
      }
      if (tag === "img" || tag === "picture") {
        var imageBlock = imageBlockFromElement(node, baseUrl, null, null);
        if (imageBlock) {
          blocks.push(imageBlock);
        }
        return;
      }
      if (tag === "figure") {
        var captionNode = node.querySelector("figcaption");
        var figureText = splitFigureTextAndCredit(captionNode ? captionNode.cloneNode(true) : null, baseUrl);
        var figureBlock = imageBlockFromElement(
          node,
          baseUrl,
          figureText.caption,
          figureText.credit
        );
        if (figureBlock) {
          blocks.push(figureBlock);
          return;
        }
      }
      if (tag === "table") {
        var tableBlock = tableBlockFromElement(node, baseUrl);
        if (tableBlock) {
          blocks.push(tableBlock);
        }
        return;
      }

      if (["article", "main", "section", "div", "body", "aside"].indexOf(tag) !== -1) {
        blocks = blocks.concat(parseBlocks(node.childNodes, baseUrl));
        return;
      }

      var nestedBlocks = parseBlocks(node.childNodes, baseUrl);
      if (nestedBlocks.length > 0) {
        blocks = blocks.concat(nestedBlocks);
        return;
      }
      var fallback = paragraphBlockFromElement(node, baseUrl);
      if (fallback) {
        blocks.push(fallback);
      }
    });
    flushInlineBuffer();
    return blocks;
  }

  function fallbackBlocksFromText(text) {
    return normalizeText(text)
      .split(/\n\s*\n/g)
      .map(function(part) {
        return normalizeText(part);
      })
      .filter(Boolean)
      .map(function(part) {
        return {
          type: "paragraph",
          content: {
            text: part,
            entities: []
          }
        };
      });
  }

  async function loadIndexDocument(indexUrl) {
    var response = await fetch(indexUrl);
    var dirtyHtml = await response.text();
    var sanitizedHtml = sanitizeHtml(dirtyHtml);
    var document = new DOMParser().parseFromString(sanitizedHtml, "text/html");
    var charset = (getCharset(document) || "utf-8").toLowerCase();
    if (["utf-8", "utf8"].indexOf(charset) === -1) {
      var binaryResponse = await fetch(indexUrl);
      var bytes = await binaryResponse.arrayBuffer();
      dirtyHtml = new TextDecoder(charset).decode(bytes);
      sanitizedHtml = sanitizeHtml(dirtyHtml);
      document = new DOMParser().parseFromString(sanitizedHtml, "text/html");
    }
    return {
      document: document,
      sanitizedHtml: sanitizedHtml
    };
  }

  async function run() {
    if (typeof window.DOMPurify === "undefined" || typeof Readability !== "function" || typeof isProbablyReaderable !== "function") {
      finish("");
      return;
    }

    var config = window.NativeDoInstantViewConfig || {};
    var pageUrl = normalizeText(config.pageUrl || window.location.href);
    var indexUrl = normalizeText(config.indexUrl || "/index.html");
    if (!pageUrl || !indexUrl) {
      finish("");
      return;
    }

    var loaded = await loadIndexDocument(indexUrl);
    var sourceDocument = loaded.document;
    if (!sourceDocument || !isProbablyReaderable(sourceDocument)) {
      finish("");
      return;
    }

    var result = new Readability(sourceDocument).parse();
    if (!result || !result.content) {
      finish("");
      return;
    }

    var readableBody = new DOMParser().parseFromString(result.content, "text/html").body;
    var blocks = parseBlocks(readableBody ? readableBody.childNodes : [], pageUrl);
    if (!blocks.length) {
      blocks = fallbackBlocksFromText(result.textContent || (readableBody && (readableBody.innerText || readableBody.textContent)) || "");
    }
    if (!blocks.length) {
      finish("");
      return;
    }

    var heroImageUrl = absoluteUrl(
      pageUrl,
      queryMeta(sourceDocument, ["meta[property='og:image']", "meta[name='twitter:image']"])
    );
    if (!heroImageUrl && readableBody) {
      var firstImage = readableBody.querySelector("img");
      heroImageUrl = bestImageUrl(firstImage, pageUrl);
    }

    finish(JSON.stringify({
      title: normalizeText(result.title || sourceDocument.title || ""),
      sourceName: normalizeText(
        result.siteName ||
          queryMeta(sourceDocument, ["meta[property='og:site_name']", "meta[name='application-name']"])
      ),
      excerpt: normalizeText(
        result.excerpt ||
          queryMeta(sourceDocument, ["meta[property='og:description']", "meta[name='description']"])
      ),
      byline: normalizeText(
        result.byline ||
          queryMeta(sourceDocument, ["meta[name='author']", "[rel='author']", ".byline", ".author", "[itemprop='author']"])
      ),
      publishedTime: normalizeText(result.publishedTime),
      heroImageUrl: heroImageUrl,
      blocks: blocks
    }));
  }

  run().catch(function() {
    finish("");
  });
})();
