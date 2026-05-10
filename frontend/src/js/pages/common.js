// ============================================
// Common UI bootstrap (Modified with full theater data)
// ============================================

(() => {
  let isInitialized = false;

  function emitLogEvent(eventType, data) {
    if (typeof window.logEvent === "function") {
      window.logEvent(eventType, data);
    }
  }

  const theaterData = {
    cgv: {
      서울: ["강남", "강변", "건대입구", "고덕강일", "구로", "대학로", "동대문", "등촌", "명동", "명동역 씨네라이브러리", "목동", "미아", "방학", "불광", "상봉", "성신여대입구", "송파", "수유", "신촌아트레온", "압구정", "여의도", "영등포", "왕십리", "용산아이파크몰", "중계", "천호", "청담씨네시티", "피카디리1958", "하계", "홍대"],
      경기: ["고양행신", "광교", "광교상현", "구리", "기흥", "김포", "김포운양", "김포풍무", "남양주", "동백", "동수원", "동탄", "동탄역", "동탄호수공원", "배곧", "범계", "부천", "부천역", "부천옥길", "분당", "산본", "서현", "성남모란", "소풍", "수원", "수원역", "시흥", "안산", "안성", "야탑", "양주옥정", "오리", "용인", "의정부", "의정부태흠", "이천", "일산", "죽전", "파주문산", "파주야당", "판교", "평촌", "평택", "평택고덕", "하남미사", "화정"],
      인천: ["계양", "부평", "송도", "인천", "인천가정", "인천도화", "인천연수", "인천학익", "주안역", "청라"],
      강원: ["강릉", "기린", "원주", "춘천"],
      "대전/충청": ["대전", "대전가오", "대전탄방", "대전터미널", "대전가양", "논산", "당진", "서산", "세종", "아산", "천안", "천안터미널", "천안펜타포트", "청주(서문)", "청주율량", "홍성"],
      대구: ["대구", "대구수성", "대구아카데미", "대구월성", "대구이시아", "대구한일", "대구현대"],
      "부산/울산": ["광복", "대한", "동래", "서면", "센텀시티", "아시아드", "하단아트몰링", "해운대", "울산삼산", "울산신천"],
      경상: ["거제", "경산", "구미", "김해", "마산", "북포항", "안동", "양산물금", "진주혁신", "창원", "창원더시티", "통영", "포항"],
      "광주/전라/제주": ["광주터미널", "광주금남로", "광주상무", "광주용봉", "광주첨단", "나주", "목포", "순천", "여수웅천", "익산", "전주효자", "제주", "제주노형"]
    },
    megabox: {
      서울: ["강남", "강동", "구의이스트폴", "군자", "더부티크목동현대백화점", "동대문", "마곡", "목동", "상봉", "상암월드컵경기장", "센트럴", "송파파크하비오", "신촌", "이수", "창동", "코엑스", "픽쳐하우스", "홍대", "화곡"],
      경기: ["고양스타필드", "광명AK플라자", "광명소하", "금정AK플라자", "김포한강신도시", "남양주현대아울렛스페이스원", "동탄", "동탄역", "미사강변(하남종합운동장)", "백석벨라시타", "별내", "부천스타필드시티", "분당", "성남모란", "수원", "수원AK플라자(수원역)", "수원남문", "수원스타필드", "수원인계", "수원호매실(휴관)", "시흥배곧", "시흥정왕", "안산중앙", "안성스타필드", "용인테크노밸리", "의정부민락", "킨텍스", "파주금촌", "파주운정", "평내호평역(구.남양주)", "평택비전", "하남스타필드"],
      인천: ["검단", "송도(트리플스트리트)", "영종", "영종하늘도시", "인천논현", "인천학익(시티오씨엘)", "청라지젤(휴관)"],
      "대전/충청/세종": ["공주", "논산", "대전신세계아트앤사이언스", "대전유성", "대전중앙로", "대전현대프리미엄아울렛", "세종(조치원)", "세종나성", "오창", "진천", "천안", "청주사창", "청주성안길", "청주터미널", "충주연수", "홍성내포"],
      "부산/대구/경상": ["경북도청", "경산하양", "구미강동", "김천", "남포항", "대구세븐밸리(칠곡)", "대구신세계(동대구)", "대구이시아", "대구프리미엄만경관", "덕천", "마산(경남대)", "부산극장", "부산대", "북대구(칠곡)", "사상", "삼천포", "서면대한", "양산", "양산증산", "울산", "진주(중안)", "창원", "창원내서", "포항", "해운대(장산)", "화명"],
      "광주/전라": ["광주상무", "광주하남", "목포하당(포르모)", "순천", "여수웅천", "전대(광주)", "전주객사", "전주혁신", "첨단"],
      강원: ["남춘천", "속초", "원주혁신", "춘천석사"],
      제주: ["제주삼화", "제주서귀포", "제주아라"]
    },
    lotte: {
      서울: ["가산디지털", "가양", "강동", "건대입구", "김포공항", "노원", "도곡", "독산", "브로드웨이", "서울대입구", "수락산", "수유", "신대방", "신도림", "신림", "에비뉴엘", "영등포", "용산", "월드타워", "은평", "장안", "중랑", "청량리", "합정", "홍대입구"],
      경기: ["광교아울렛", "광명", "광명아울렛", "광주터미널", "구리아울렛", "동탄", "라페스타", "마석", "별내", "병점", "부천", "부천역", "북수원", "산본피트인", "서수원", "성남중앙", "센트럴락", "송탄", "수원", "수지", "시화", "안산", "안산고잔", "안성", "안양일번가", "오산", "용인기흥", "용인역북", "위례", "의정부민락", "인덕원", "주엽", "진접", "파주야당", "파주운정", "판교", "평촌", "하남미사", "향남"],
      인천: ["부평", "부평역사", "영종하늘도시", "인천아시아드", "인천터미널"],
      "대전/충청": ["대전", "대전관저", "대전센트럴", "당진", "서산", "아산터미널", "천안불당", "천안청당", "서청주", "청주용암", "충주"],
      "부산/울산": ["광복", "대영", "동래", "동부산아울렛", "부산명지", "부산본점", "사상", "서면", "센텀시티", "오투(부산대)", "프리미엄해운대", "울산", "울산성남"],
      "대구/경상": ["대구광장", "대구율하", "대구현풍", "동성로", "상인", "성서", "프리미엄만경", "프리미엄칠곡", "경산", "경주", "경주황성", "구미공단", "상주", "영주", "영천", "포항", "프리미엄구미센트럴", "프리미엄안동", "거창", "김해부원", "김해아울렛(장유)", "사천", "마산(합성동)", "양산물금", "진주엠비씨네", "진주혁신(롯데몰)", "진해", "창원", "통영", "프리미엄경남대", "프리미엄진주(중안)"],
      "광주/전라": ["광주", "광주광산", "수완", "충장로", "군산몰", "군산나운", "익산모현", "전주", "전주평화"],
      강원: ["남원주", "동해", "속초", "원주무실", "춘천"],
      제주: ["서귀포", "제주연동"]
    },
  };

  function renderTheaters(grid, brand) {
    const data = theaterData[brand];
    if (!grid || !data) {
      return;
    }

    let html = "";

    for (const region in data) {
      html += `
        <div class="region-row">
          <div class="region-name">${region}</div>
          <div class="theater-list">
            ${data[region].map((name) => `<span class="theater-item">${name}</span>`).join("")}
          </div>
        </div>
      `;
    }

    grid.innerHTML = html;
  }

  function initCommonUi() {
    if (isInitialized) {
      return;
    }

    const fixedHeader = document.getElementById("fixedHeader");
    const headerSearchBtn = document.getElementById("headerSearchBtn");
    const scrollToTopBtn = document.getElementById("scrollToTop");
    const theaterSearchBtn = document.getElementById("theaterSearchBtn");
    const overlay = document.getElementById("theaterOverlay");
    const closeBtn = document.getElementById("closeOverlay");
    const grid = document.getElementById("theaterGrid");
    const tabItems = Array.from(document.querySelectorAll(".overlay-tab-item"));
    const dropdown = document.querySelector(".custom-dropdown");
    const selected = dropdown?.querySelector(".selected");
    const items = Array.from(dropdown?.querySelectorAll(".dropdown-list li") || []);

    if (!fixedHeader && !scrollToTopBtn && !theaterSearchBtn && !dropdown) {
      return;
    }

    isInitialized = true;

    const handleScroll = () => {
      const scrollTop = window.scrollY;

      if (fixedHeader) {
        const shouldShowHeader = scrollTop > 100;

        if (shouldShowHeader && !fixedHeader.classList.contains("visible")) {
          fixedHeader.classList.add("visible");
          document.body.classList.add("header-visible");
          emitLogEvent("고정 헤더", { action: "표시", scrollPosition: scrollTop });
        } else if (!shouldShowHeader && fixedHeader.classList.contains("visible")) {
          fixedHeader.classList.remove("visible");
          document.body.classList.remove("header-visible");
          emitLogEvent("고정 헤더", { action: "숨김", scrollPosition: scrollTop });
        }
      }

      if (scrollToTopBtn) {
        if (scrollTop > 300 && !scrollToTopBtn.classList.contains("visible")) {
          scrollToTopBtn.classList.add("visible");
          emitLogEvent("맨 위로 버튼", { action: "표시", scrollPosition: scrollTop });
        } else if (scrollTop <= 300 && scrollToTopBtn.classList.contains("visible")) {
          scrollToTopBtn.classList.remove("visible");
          emitLogEvent("맨 위로 버튼", { action: "숨김", scrollPosition: scrollTop });
        }
      }
    };

    window.addEventListener("scroll", handleScroll, { passive: true });
    handleScroll();

    headerSearchBtn?.addEventListener("click", () => {
      emitLogEvent("헤더 검색 버튼", { action: "클릭" });

      const movieInput = document.getElementById("movieInput");
      const searchCard = document.querySelector(".search-card");

      if (movieInput && searchCard) {
        movieInput.focus();
        searchCard.scrollIntoView({ behavior: "smooth" });
        return;
      }

      const homeUrl = headerSearchBtn.dataset.homeUrl;
      if (homeUrl) {
        window.location.href = homeUrl;
      }
    });

    scrollToTopBtn?.addEventListener("click", () => {
      emitLogEvent("맨 위로 버튼", { action: "클릭" });
      window.scrollTo({ top: 0, behavior: "smooth" });
    });

    theaterSearchBtn?.addEventListener("click", (event) => {
      if (!overlay) {
        return;
      }

      event.stopPropagation();
      overlay.classList.toggle("active");

      if (overlay.classList.contains("active")) {
        const activeTab = document.querySelector(".overlay-tab-item.active");
        if (activeTab) {
          renderTheaters(grid, activeTab.dataset.brand);
        }
      }
    });

    closeBtn?.addEventListener("click", () => {
      overlay?.classList.remove("active");
    });

    tabItems.forEach((tab) => {
      tab.addEventListener("click", () => {
        tabItems.forEach((item) => item.classList.remove("active"));
        tab.classList.add("active");
        renderTheaters(grid, tab.dataset.brand);
      });
    });

    selected?.addEventListener("click", (event) => {
      event.stopPropagation();
      dropdown.classList.toggle("active");
    });

    items.forEach((item) => {
      item.addEventListener("click", () => {
        const url = item.dataset.url;

        if (selected) {
          selected.textContent = item.textContent;
        }

        dropdown?.classList.remove("active");

        if (!url) {
          return;
        }

        if (url.startsWith("http")) {
          window.open(url, "_blank", "noopener");
          return;
        }

        window.location.href = url;
      });
    });

    document.addEventListener("click", (event) => {
      if (dropdown && !dropdown.contains(event.target)) {
        dropdown.classList.remove("active");
      }

      if (overlay?.classList.contains("active") && !overlay.contains(event.target) && event.target !== theaterSearchBtn) {
        overlay.classList.remove("active");
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initCommonUi, { once: true });
  } else {
    initCommonUi();
  }

  document.addEventListener("daboyeo:components-loaded", initCommonUi);
  window.initCommonUi = initCommonUi;
})();