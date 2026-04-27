package com.chg.yuaicodemother.controller;

import com.chg.yuaicodemother.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;


    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<?> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isBlank()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            if (isHtml(filePath) && "1".equals(request.getParameter("visualEdit"))) {
                String html = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header("Content-Type", getContentTypeWithCharset(filePath))
                        .body(injectVisualEditBridge(html));
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private boolean isHtml(String filePath) {
        return filePath.endsWith(".html");
    }

    private String injectVisualEditBridge(String html) {
        if (html.contains("id=\"ai-visual-edit-bridge\"")) {
            return html;
        }
        String bridge = """
                <script id="ai-visual-edit-bridge">
                (function () {
                  if (window.__AI_VISUAL_EDIT_BRIDGE__) return;
                  window.__AI_VISUAL_EDIT_BRIDGE__ = true;
                  var enabled = false;
                  var hoverEl = null;
                  var selectedEl = null;
                  var parentOrigin = '*';

                  function ensureStyle() {
                    if (document.getElementById('ai-visual-edit-style')) return;
                    var style = document.createElement('style');
                    style.id = 'ai-visual-edit-style';
                    style.textContent = '.ai-edit-hover{outline:2px dashed #1677ff!important;outline-offset:2px!important;cursor:crosshair!important}.ai-edit-selected{outline:3px solid #52c41a!important;outline-offset:2px!important}';
                    document.head.appendChild(style);
                  }

                  function classNameOf(el) {
                    if (!el.className) return '';
                    return typeof el.className === 'string' ? el.className : (el.className.baseVal || '');
                  }

                  function selectorOf(el) {
                    if (el.getAttribute('data-ai-id')) return '[data-ai-id=\"' + el.getAttribute('data-ai-id') + '\"]';
                    var path = [];
                    var cur = el;
                    while (cur && cur !== document.body && cur.nodeType === 1) {
                      var selector = cur.tagName.toLowerCase();
                      if (cur.id) {
                        selector += '#' + cur.id;
                        path.unshift(selector);
                        break;
                      }
                      var cls = classNameOf(cur).split(/\\s+/).filter(function (c) { return c && c.indexOf('ai-edit-') !== 0; });
                      if (cls.length) selector += '.' + cls.join('.');
                      var siblings = Array.prototype.slice.call(cur.parentElement ? cur.parentElement.children : []);
                      selector += ':nth-child(' + (siblings.indexOf(cur) + 1) + ')';
                      path.unshift(selector);
                      cur = cur.parentElement;
                    }
                    return path.join(' > ');
                  }

                  function infoOf(el) {
                    var rect = el.getBoundingClientRect();
                    var style = window.getComputedStyle(el);
                    return {
                      nodeId: el.getAttribute('data-ai-id') || '',
                      tagName: el.tagName,
                      id: el.id || '',
                      className: classNameOf(el),
                      text: (el.textContent || '').trim().slice(0, 200),
                      textContent: (el.textContent || '').trim().slice(0, 200),
                      selector: selectorOf(el),
                      outerHTML: (el.outerHTML || '').slice(0, 5000),
                      pagePath: window.location.search + window.location.hash,
                      computedStyle: {
                        color: style.color,
                        backgroundColor: style.backgroundColor,
                        fontSize: style.fontSize,
                        fontWeight: style.fontWeight,
                        borderRadius: style.borderRadius,
                        display: style.display
                      },
                      rect: { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
                    };
                  }

                  function clearHover() {
                    if (hoverEl) hoverEl.classList.remove('ai-edit-hover');
                    hoverEl = null;
                  }

                  function clearSelected() {
                    if (selectedEl) selectedEl.classList.remove('ai-edit-selected');
                    selectedEl = null;
                  }

                  document.addEventListener('mouseover', function (event) {
                    if (!enabled) return;
                    var target = event.target;
                    if (!target || target === document.body || target === document.documentElement || target === selectedEl) return;
                    clearHover();
                    target.classList.add('ai-edit-hover');
                    hoverEl = target;
                  }, true);

                  document.addEventListener('mouseout', function () {
                    if (enabled) clearHover();
                  }, true);

                  document.addEventListener('click', function (event) {
                    if (!enabled) return;
                    event.preventDefault();
                    event.stopPropagation();
                    var target = event.target;
                    if (!target || target === document.body || target === document.documentElement) return;
                    clearHover();
                    clearSelected();
                    target.classList.add('ai-edit-selected');
                    selectedEl = target;
                    window.parent.postMessage({ type: 'AI_ELEMENT_SELECTED', data: { elementInfo: infoOf(target) } }, parentOrigin);
                  }, true);

                  window.addEventListener('message', function (event) {
                    var data = event.data || {};
                    if (data.type !== 'AI_VISUAL_EDIT_MODE' && data.type !== 'AI_VISUAL_EDIT_CLEAR') return;
                    parentOrigin = event.origin || '*';
                    if (data.type === 'AI_VISUAL_EDIT_CLEAR') {
                      clearHover();
                      clearSelected();
                      return;
                    }
                    enabled = !!data.enabled;
                    ensureStyle();
                    if (!enabled) {
                      clearHover();
                      clearSelected();
                    }
                  });
                })();
                </script>
                """;
        int bodyIndex = html.toLowerCase().lastIndexOf("</body>");
        if (bodyIndex >= 0) {
            return html.substring(0, bodyIndex) + bridge + html.substring(bodyIndex);
        }
        return html + bridge;
    }
}
