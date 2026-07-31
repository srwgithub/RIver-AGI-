(function () {
  const moduleMap = {
    'annotation-platform': {
      title: '数据采集与标注平台',
      description: '上传多源数据，配置标签体系，创建任务并分配给标注员完成协同标注。',
      steps: ['先上传数据或媒体', '配置标签体系', '创建并分配任务', '提交后进入质检']
    },
    'annotation-quality': {
      title: '标注质量管理模块',
      description: '审核标注结果，检查一致性，评估标注员绩效，并处理争议仲裁。',
      steps: ['选择待审任务', '运行一致性检查', '审核或纠偏', '生成质量报告']
    },
    'prediction-engine': {
      title: '市场需求预测引擎',
      description: '创建预测任务，选择算法训练模型，管理版本并执行 A/B 测试。',
      steps: ['选择数据和字段', '选择模型算法', '提交预测训练', '查看版本和测试结果']
    },
    'trend-dashboard': {
      title: '趋势分析与可视化',
      description: '查看趋势走势、异常波动、根因贡献，并生成可视化分析报告。',
      steps: ['运行趋势分析', '检测异常', '查看根因', '导出报告']
    },
    'prediction-evaluation': {
      title: '预测结果评估与优化',
      description: '评估预测准确率和偏差，监控模型表现，执行自动调优和重训练。',
      steps: ['查看准确率', '配置监控阈值', '启动自动优化', '应用最优参数']
    },
    'security-audit': {
      title: '数据管理与安全审计',
      description: '查询操作日志，执行安全扫描、权限控制、脱敏、备份恢复和合规报告。',
      steps: ['查询审计日志', '执行安全扫描', '处理风险', '导出或备份']
    }
  };

  function currentModule() {
    const path = window.location.pathname;
    return Object.keys(moduleMap).find(key => path.includes(key)) || 'dashboard';
  }

  const moduleName = currentModule();
  const fallback = moduleMap[moduleName] || {
    title: 'RIver AGI 模块',
    description: '用于完成当前业务流程的数据处理和智能分析。',
    steps: ['选择数据', '执行操作', '查看结果', '继续下一步']
  };

  function injectStyle() {
    if (document.getElementById('river-module-interaction-style')) return;
    const style = document.createElement('style');
    style.id = 'river-module-interaction-style';
    style.textContent = `
      .river-guide-card {
        margin: 14px 20px 0;
        padding: 13px 16px;
        background: #fff;
        border: 1px solid #dfe6ef;
        border-radius: 9px;
        box-shadow: 0 2px 8px rgba(15,23,42,.035);
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        color: #202936;
      }
      .river-guide-card h3 {
        margin: 0 0 4px;
        font-size: 15px;
        color: #202936 !important;
        background: none !important;
        -webkit-text-fill-color: currentColor !important;
      }
      .river-guide-card p {
        margin: 0;
        font-size: 12px;
        color: #687586;
        line-height: 1.55;
      }
      .river-guide-steps {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        justify-content: flex-end;
        min-width: 280px;
      }
      .river-guide-steps span {
        padding: 5px 9px;
        border-radius: 999px;
        background: #e2f3f0;
        border: 1px solid #b8dfd8;
        color: #0f766e;
        font-size: 11px;
        white-space: nowrap;
      }
      .river-toast {
        position: fixed;
        right: 22px;
        bottom: 22px;
        z-index: 99999;
        max-width: 360px;
        padding: 12px 14px;
        border-radius: 9px;
        background: #fff;
        border: 1px solid #b8dfd8;
        color: #202936;
        box-shadow: 0 12px 30px rgba(15,23,42,.14);
        font-size: 13px;
        line-height: 1.5;
        transform: translateY(8px);
        opacity: 0;
        transition: all .18s ease;
      }
      .river-toast.show {
        transform: translateY(0);
        opacity: 1;
      }
      .river-toast b {
        color: #0f766e;
      }
      .river-action-loading {
        opacity: .72;
        pointer-events: none;
      }
      @media (max-width: 900px) {
        .river-guide-card {
          flex-direction: column;
          align-items: flex-start;
        }
        .river-guide-steps {
          justify-content: flex-start;
          min-width: 0;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function showToast(message, title) {
    const old = document.querySelector('.river-toast');
    if (old) old.remove();
    const toast = document.createElement('div');
    toast.className = 'river-toast';
    toast.innerHTML = `<b>${title || '操作反馈'}</b><br>${message}`;
    document.body.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('show'));
    setTimeout(() => {
      toast.classList.remove('show');
      setTimeout(() => toast.remove(), 220);
    }, 2800);
  }

  function renderGuide(summary) {
    if (document.querySelector('.river-guide-card')) return;
    const data = summary || fallback;
    const guide = document.createElement('section');
    guide.className = 'river-guide-card';
    guide.innerHTML = `
      <div>
        <h3>${data.title || fallback.title}</h3>
        <p>${data.description || fallback.description}</p>
      </div>
      <div class="river-guide-steps">
        ${(data.steps || fallback.steps).map((step, index) => `<span>${index + 1}. ${step}</span>`).join('')}
      </div>
    `;
    const header = document.querySelector('.header, .dashboard-header, .topbar, header');
    if (header && header.parentNode) {
      header.insertAdjacentElement('afterend', guide);
    } else {
      document.body.insertAdjacentElement('afterbegin', guide);
    }
  }

  async function fetchSummary() {
    try {
      const response = await fetch(`/api/v1/module-actions/summary?module=${encodeURIComponent(moduleName)}`);
      const payload = await response.json();
      renderGuide(payload.data || fallback);
    } catch (error) {
      renderGuide(fallback);
    }
  }

  async function executeAction(action, element) {
    if (!action || action === '×' || action === '✕') return;
    const originalText = element && element.textContent;
    element && element.classList.add('river-action-loading');
    try {
      // Real page handlers own business requests; this listener only provides feedback
      // for controls that do not have a dedicated handler.
      showToast(`请在当前页面完成「${action}」操作`, fallback.title);
    } catch (error) {
      showToast(`已触发「${action}」，当前后端连接不可用，页面已保留本地交互反馈。`, fallback.title);
    } finally {
      element && element.classList.remove('river-action-loading');
      if (element && originalText) element.textContent = originalText;
    }
  }

  function bindActions() {
    document.addEventListener('click', event => {
      const target = event.target.closest('button, .btn, .kpi-btn, .action-link, .switch, .toggle-switch, .permission-item, .arbitration-result, .upload-zone');
      if (!target || target.closest('.modal-close')) return;
      const text = (target.innerText || target.textContent || target.getAttribute('aria-label') || '页面操作').trim().replace(/\s+/g, ' ');
      window.setTimeout(() => executeAction(text, target), 0);
    });

    document.addEventListener('change', event => {
      const target = event.target;
      if (target && target.matches('input[type="file"]')) {
        const count = target.files ? target.files.length : 0;
        executeAction(`上传文件 ${count} 个`, target);
      }
    });
  }

  function replaceAlert() {
    const nativeAlert = window.alert;
    window.alert = function (message) {
      showToast(String(message || '操作已完成'), fallback.title);
      return undefined;
    };
    window.__riverNativeAlert = nativeAlert;
  }

  function init() {
    injectStyle();
    replaceAlert();
    fetchSummary();
    bindActions();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
