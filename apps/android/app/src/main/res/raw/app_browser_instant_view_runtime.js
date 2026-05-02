(async function () {
    try {
        const getCharset = document => {
            let meta = document.querySelector('meta[charset]')
            if (meta) return meta.getAttribute('charset') || 'utf-8'
            meta = document.querySelector('meta[http-equiv="Content-Type"]')
            if (!meta) return 'utf-8'
            const props = Object.fromEntries((meta.getAttribute('content') || '').split(/; */g).map(s => {
                const index = s.indexOf('=')
                if (index < 0) return [s, true]
                return [s.substring(0, index), s.substring(index + 1)]
            }))
            return props.charset || 'utf-8'
        }
        let dirty_html = await (await fetch('/index.html')).text()
        let html = DOMPurify.sanitize(dirty_html, {
            WHOLE_DOCUMENT: true,
            ADD_TAGS: ['iframe', 'meta'],
            ADD_ATTR: ['http-equiv', 'content']
        })
        let document = new DOMParser().parseFromString(html, 'text/html')
        const charset = getCharset(document)
        if (['utf-8', 'utf8'].indexOf((charset || '').toLowerCase()) < 0) {
            dirty_html = new TextDecoder(charset).decode(await (await fetch('/index.html')).arrayBuffer())
            html = DOMPurify.sanitize(dirty_html, {
                WHOLE_DOCUMENT: true,
                ADD_TAGS: ['iframe', 'meta'],
                ADD_ATTR: ['http-equiv', 'content']
            })
            document = new DOMParser().parseFromString(html, 'text/html')
        }

        if (!isProbablyReaderable(document)) {
            console.error('isProbablyReaderable = false')
            window.Instant.done('null')
            return
        }

        const readability = new Readability(document)
        const result = readability.parse()
        const doc = new DOMParser().parseFromString(result.content, 'text/html').body

        const parse = e => [...(e.childNodes || [])].map(node => {
            if (node.nodeType === Node.COMMENT_NODE) return []
            if (!node.tagName || node.nodeType === Node.TEXT_NODE) {
                return node.textContent.trim() ? [ node.textContent ] : []
            }
            const tag = node.tagName.toLowerCase()
            const content = parse(node)
            const attr = [...(node.attributes || [])]
                .map(a => [a.name, a.textContent])
                .filter(([k, v]) => !!k && !!v)
            const style = getComputedStyle(node)
            if (+style['font-weight'] >= 600) attr.push(['bold', 1])
            if (+style['font-style'] === 'italic') attr.push(['italic', 1])
            if (style['text-align'] === 'center') attr.push(['xcenter', 1])
            if (style['vertical-align'] === 'middle') attr.push(['ycenter', 1])
            const attrobj = Object.fromEntries(attr)
            if (tag === 'img') {
                if (!attrobj['width']) attrobj['width'] = ''+node.clientWidth
                if (!attrobj['height']) attrobj['height'] = ''+node.clientHeight
            }
            return [ { tag, content, ...attrobj } ]
        }).flat();

        if ($DEBUG$) {
            console.log({
                ...result,
                html,
                content: parse(doc)
            })
        }

        window.Instant.done(JSON.stringify({
            ...result,
            url: window.location.href,
            content: parse(doc)
        }))
    } catch (e) {
        console.error(e)
        window.Instant.done('null')
    }
})();
