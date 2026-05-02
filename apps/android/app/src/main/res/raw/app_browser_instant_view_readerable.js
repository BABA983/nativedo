(function() {
  var REGEXPS = {
    unlikelyCandidates: /-ad-|ai2html|banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|foot|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote/i,
    okMaybeItsACandidate: /and|article|body|column|main|shadow/i
  };

  function isNodeVisible(node) {
    return (!node.style || node.style.display !== "none") && !node.hasAttribute("hidden");
  }

  window.isProbablyReaderable = function(document, visibilityChecker) {
    visibilityChecker = visibilityChecker || isNodeVisible;
    var nodes = document.querySelectorAll("p, pre");
    var brNodes = document.querySelectorAll("div > br");
    if (brNodes.length) {
      var nodeSet = new Set(nodes);
      Array.prototype.forEach.call(brNodes, function(node) {
        nodeSet.add(node.parentNode);
      });
      nodes = Array.from(nodeSet);
    }
    var score = 0;
    return Array.prototype.some.call(nodes, function(node) {
      if (!visibilityChecker(node)) {
        return false;
      }
      var matchString = (node.className || "") + " " + (node.id || "");
      if ((REGEXPS.unlikelyCandidates.test(matchString) && !REGEXPS.okMaybeItsACandidate.test(matchString)) || node.matches("li p")) {
        return false;
      }
      var textLength = (node.textContent || "").trim().length;
      if (textLength < 140) {
        return false;
      }
      score += Math.sqrt(textLength - 140);
      return score > 20;
    });
  };
})();
