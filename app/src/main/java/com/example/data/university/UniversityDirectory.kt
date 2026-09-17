package com.example.data.university

object UniversityDirectory {

    val colleges: List<CollegeInfo> = listOf(
        // ==========================================
        // 1. INSTITUTES OF NATIONAL IMPORTANCE (INIs & AIIMS)
        // ==========================================
        CollegeInfo(
            id = "aiims_delhi",
            name = "All India Institute of Medical Sciences (AIIMS), New Delhi",
            shortName = "AIIMS New Delhi",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Delhi",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Ansari Nagar, New Delhi"
        ),
        CollegeInfo(
            id = "aiims_rishikesh",
            name = "All India Institute of Medical Sciences (AIIMS), Rishikesh",
            shortName = "AIIMS Rishikesh",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Uttarakhand",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Virbhadra Road, Rishikesh"
        ),
        CollegeInfo(
            id = "aiims_bhopal",
            name = "All India Institute of Medical Sciences (AIIMS), Bhopal",
            shortName = "AIIMS Bhopal",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Madhya Pradesh",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Saket Nagar, Bhopal"
        ),
        CollegeInfo(
            id = "aiims_jodhpur",
            name = "All India Institute of Medical Sciences (AIIMS), Jodhpur",
            shortName = "AIIMS Jodhpur",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Rajasthan",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Basni Industrial Area, Jodhpur"
        ),
        CollegeInfo(
            id = "aiims_patna",
            name = "All India Institute of Medical Sciences (AIIMS), Patna",
            shortName = "AIIMS Patna",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Bihar",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Phulwarisharif, Patna"
        ),
        CollegeInfo(
            id = "aiims_bhubaneswar",
            name = "All India Institute of Medical Sciences (AIIMS), Bhubaneswar",
            shortName = "AIIMS Bhubaneswar",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Odisha",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Sijua, Patrapada, Bhubaneswar"
        ),
        CollegeInfo(
            id = "aiims_raipur",
            name = "All India Institute of Medical Sciences (AIIMS), Raipur",
            shortName = "AIIMS Raipur",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Chhattisgarh",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "Tatibandh, GE Road, Raipur"
        ),
        CollegeInfo(
            id = "aiims_nagpur",
            name = "All India Institute of Medical Sciences (AIIMS), Nagpur",
            shortName = "AIIMS Nagpur",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Maharashtra",
            universityAffiliation = "AIIMS Autonomous (INI)",
            campusLocation = "MIHAN, Nagpur"
        ),
        CollegeInfo(
            id = "pgimer_chd",
            name = "Postgraduate Institute of Medical Education and Research (PGIMER), Chandigarh",
            shortName = "PGIMER Chandigarh",
            category = CollegeCategory.CENTRAL_INI,
            stream = "Postgraduate & Super-Specialty / Allied",
            state = "Chandigarh",
            universityAffiliation = "PGIMER Autonomous (INI)",
            campusLocation = "Sector 12, Chandigarh"
        ),
        CollegeInfo(
            id = "jipmer_puducherry",
            name = "Jawaharlal Institute of Postgraduate Medical Education and Research (JIPMER), Puducherry",
            shortName = "JIPMER Puducherry",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Puducherry",
            universityAffiliation = "JIPMER Autonomous (INI)",
            campusLocation = "Dhanvantari Nagar, Puducherry"
        ),
        CollegeInfo(
            id = "ims_bhu",
            name = "Institute of Medical Sciences (IMS BHU), Banaras Hindu University",
            shortName = "IMS BHU Varanasi",
            category = CollegeCategory.CENTRAL_INI,
            stream = "MBBS / MD / MS",
            state = "Uttar Pradesh",
            universityAffiliation = "Banaras Hindu University (Central University)",
            campusLocation = "Varanasi, UP"
        ),
        CollegeInfo(
            id = "jnmch_amu",
            name = "Jawaharlal Nehru Medical College (JNMC AMU), Aligarh",
            shortName = "JNMC AMU Aligarh",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Uttar Pradesh",
            universityAffiliation = "Aligarh Muslim University (Central University)",
            campusLocation = "Aligarh, UP"
        ),

        // ==========================================
        // 2. PREMIER ALLOPATHIC COLLEGES (MBBS / BDS)
        // ==========================================
        CollegeInfo(
            id = "mamc_delhi",
            name = "Maulana Azad Medical College (MAMC), New Delhi",
            shortName = "MAMC New Delhi",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Delhi",
            universityAffiliation = "University of Delhi (DU)",
            campusLocation = "Bahadur Shah Zafar Marg, New Delhi"
        ),
        CollegeInfo(
            id = "lhmc_delhi",
            name = "Lady Hardinge Medical College (LHMC), New Delhi",
            shortName = "LHMC New Delhi",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Delhi",
            universityAffiliation = "University of Delhi (DU)",
            campusLocation = "Connaught Place, New Delhi"
        ),
        CollegeInfo(
            id = "vmmc_delhi",
            name = "Vardhman Mahavir Medical College & Safdarjung Hospital (VMMC)",
            shortName = "VMMC & Safdarjung",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Delhi",
            universityAffiliation = "Guru Gobind Singh Indraprastha University (GGSIPU)",
            campusLocation = "Ring Road, New Delhi"
        ),
        CollegeInfo(
            id = "ucms_delhi",
            name = "University College of Medical Sciences (UCMS) & GTB Hospital",
            shortName = "UCMS Delhi",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Delhi",
            universityAffiliation = "University of Delhi (DU)",
            campusLocation = "Dilshad Garden, Delhi"
        ),
        CollegeInfo(
            id = "kgmu_lucknow",
            name = "King George's Medical University (KGMU), Lucknow",
            shortName = "KGMU Lucknow",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS / BDS",
            state = "Uttar Pradesh",
            universityAffiliation = "King George's Medical University",
            campusLocation = "Shah Mina Road, Chowk, Lucknow"
        ),
        CollegeInfo(
            id = "seth_gs_kem",
            name = "Seth GS Medical College & KEM Hospital, Mumbai",
            shortName = "Seth GS & KEM Mumbai",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Maharashtra",
            universityAffiliation = "Maharashtra University of Health Sciences (MUHS)",
            campusLocation = "Parel, Mumbai"
        ),
        CollegeInfo(
            id = "grant_med_college",
            name = "Grant Government Medical College & Sir JJ Group of Hospitals, Mumbai",
            shortName = "Grant Medical College Mumbai",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Maharashtra",
            universityAffiliation = "Maharashtra University of Health Sciences (MUHS)",
            campusLocation = "Byculla, Mumbai"
        ),
        CollegeInfo(
            id = "cmc_vellore",
            name = "Christian Medical College (CMC), Vellore",
            shortName = "CMC Vellore",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Tamil Nadu",
            universityAffiliation = "The Tamil Nadu Dr. M.G.R. Medical University",
            campusLocation = "Vellore, Tamil Nadu"
        ),
        CollegeInfo(
            id = "afmc_pune",
            name = "Armed Forces Medical College (AFMC), Pune",
            shortName = "AFMC Pune",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Maharashtra",
            universityAffiliation = "Maharashtra University of Health Sciences (MUHS)",
            campusLocation = "Wanowrie, Pune"
        ),
        CollegeInfo(
            id = "mmc_chennai",
            name = "Madras Medical College & Rajiv Gandhi Government General Hospital",
            shortName = "Madras Medical College",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Tamil Nadu",
            universityAffiliation = "The Tamil Nadu Dr. M.G.R. Medical University",
            campusLocation = "EVR Periyar Salai, Park Town, Chennai"
        ),
        CollegeInfo(
            id = "bmcri_bangalore",
            name = "Bangalore Medical College and Research Institute (BMCRI)",
            shortName = "BMCRI Bengaluru",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Karnataka",
            universityAffiliation = "Rajiv Gandhi University of Health Sciences (RGUHS)",
            campusLocation = "Fort, K.R. Market, Bengaluru"
        ),
        CollegeInfo(
            id = "cmc_kolkata",
            name = "Medical College Kolkata (Calcutta Medical College)",
            shortName = "Medical College Kolkata",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "West Bengal",
            universityAffiliation = "West Bengal University of Health Sciences (WBUHS)",
            campusLocation = "College Street, Kolkata"
        ),
        CollegeInfo(
            id = "ipgmer_kolkata",
            name = "Institute of Post-Graduate Medical Education and Research (IPGMER & SSKM Hospital)",
            shortName = "IPGMER & SSKM Kolkata",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "West Bengal",
            universityAffiliation = "West Bengal University of Health Sciences (WBUHS)",
            campusLocation = "AJC Bose Road, Kolkata"
        ),
        CollegeInfo(
            id = "gmc_srinagar",
            name = "Government Medical College (GMC), Srinagar",
            shortName = "GMC Srinagar",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Jammu & Kashmir",
            universityAffiliation = "University of Kashmir",
            campusLocation = "Karan Nagar, Srinagar"
        ),
        CollegeInfo(
            id = "gmc_jammu",
            name = "Government Medical College (GMC), Jammu",
            shortName = "GMC Jammu",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Jammu & Kashmir",
            universityAffiliation = "University of Jammu",
            campusLocation = "Bakshi Nagar, Jammu"
        ),
        CollegeInfo(
            id = "skims_srinagar",
            name = "Sher-i-Kashmir Institute of Medical Sciences (SKIMS), Srinagar",
            shortName = "SKIMS Soura",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Jammu & Kashmir",
            universityAffiliation = "SKIMS Deemed University",
            campusLocation = "Soura, Srinagar"
        ),
        CollegeInfo(
            id = "osmania_hyderabad",
            name = "Osmania Medical College, Hyderabad",
            shortName = "Osmania Medical College",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Telangana",
            universityAffiliation = "Kaloji Narayana Rao University of Health Sciences (KNRUHS)",
            campusLocation = "Koti, Hyderabad"
        ),
        CollegeInfo(
            id = "bjmc_ahmedabad",
            name = "B.J. Medical College & Civil Hospital, Ahmedabad",
            shortName = "BJMC Ahmedabad",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Gujarat",
            universityAffiliation = "Gujarat University",
            campusLocation = "Asarwa, Ahmedabad"
        ),
        CollegeInfo(
            id = "sms_jaipur",
            name = "Sawai Man Singh (SMS) Medical College, Jaipur",
            shortName = "SMS Medical College Jaipur",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Rajasthan",
            universityAffiliation = "Rajasthan University of Health Sciences (RUHS)",
            campusLocation = "JLN Marg, Jaipur"
        ),
        CollegeInfo(
            id = "pmch_patna",
            name = "Patna Medical College and Hospital (PMCH), Patna",
            shortName = "PMCH Patna",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Bihar",
            universityAffiliation = "Aryabhatta Knowledge University (AKU)",
            campusLocation = "Ashok Rajpath, Patna"
        ),
        CollegeInfo(
            id = "kmc_manipal",
            name = "Kasturba Medical College (KMC), Manipal",
            shortName = "KMC Manipal",
            category = CollegeCategory.ALLOPATHIC,
            stream = "MBBS / MD / MS",
            state = "Karnataka",
            universityAffiliation = "Manipal Academy of Higher Education (MAHE)",
            campusLocation = "Madhav Nagar, Manipal"
        ),

        // ==========================================
        // 3. AYUSH COLLEGES & UNIVERSITIES
        // (Ayurveda, Homeopathy, Unani, Siddha, Naturopathy)
        // ==========================================

        // --- AYURVEDA (BAMS) ---
        CollegeInfo(
            id = "nia_jaipur",
            name = "National Institute of Ayurveda (NIA), Jaipur",
            shortName = "NIA Jaipur",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda) / MD (Ayurveda)",
            state = "Rajasthan",
            universityAffiliation = "National Institute of Ayurveda (Deemed to be University)",
            campusLocation = "Jorawar Singh Gate, Amer Road, Jaipur",
            isAyush = true
        ),
        CollegeInfo(
            id = "aiia_delhi",
            name = "All India Institute of Ayurveda (AIIA), New Delhi",
            shortName = "AIIA New Delhi",
            category = CollegeCategory.AYUSH,
            stream = "BAMS / MD / MS (Ayurveda)",
            state = "Delhi",
            universityAffiliation = "Ministry of AYUSH Apex Institute (Deemed)",
            campusLocation = "Gautampuri, Sarita Vihar, New Delhi",
            isAyush = true
        ),
        CollegeInfo(
            id = "itra_jamnagar",
            name = "Institute of Teaching and Research in Ayurveda (ITRA), Jamnagar",
            shortName = "ITRA Jamnagar",
            category = CollegeCategory.AYUSH,
            stream = "BAMS / MD (Ayurveda)",
            state = "Gujarat",
            universityAffiliation = "ITRA Autonomous (Institute of National Importance)",
            campusLocation = "Gujarat Ayurved University Campus, Jamnagar",
            isAyush = true
        ),
        CollegeInfo(
            id = "bhu_ayurveda",
            name = "Faculty of Ayurveda, Institute of Medical Sciences, BHU",
            shortName = "Faculty of Ayurveda BHU",
            category = CollegeCategory.AYUSH,
            stream = "BAMS / MD / MS (Ayurveda)",
            state = "Uttar Pradesh",
            universityAffiliation = "Banaras Hindu University (Central University)",
            campusLocation = "Varanasi, UP",
            isAyush = true
        ),
        CollegeInfo(
            id = "state_ayurvedic_lucknow",
            name = "State Ayurvedic College and Hospital, Lucknow",
            shortName = "State Ayurvedic College Lucknow",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda)",
            state = "Uttar Pradesh",
            universityAffiliation = "Atal Bihari Vajpayee Medical University (ABVMU)",
            campusLocation = "Tulsidas Marg, Lucknow",
            isAyush = true
        ),
        CollegeInfo(
            id = "tibbia_ayurveda_delhi",
            name = "Ayurvedic and Unani Tibbia College (Ayurveda Faculty), Karol Bagh",
            shortName = "A&U Tibbia College (Ayurveda)",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda)",
            state = "Delhi",
            universityAffiliation = "University of Delhi (DU)",
            campusLocation = "Karol Bagh, New Delhi",
            isAyush = true
        ),
        CollegeInfo(
            id = "gamc_bangalore",
            name = "Government Ayurvedic Medical College (GAMC), Bengaluru",
            shortName = "GAMC Bengaluru",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda)",
            state = "Karnataka",
            universityAffiliation = "Rajiv Gandhi University of Health Sciences (RGUHS)",
            campusLocation = "Dhanvanthri Road, Bengaluru",
            isAyush = true
        ),
        CollegeInfo(
            id = "govt_ayurveda_tvm",
            name = "Government Ayurveda College, Thiruvananthapuram",
            shortName = "Govt Ayurveda College Trivandrum",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda)",
            state = "Kerala",
            universityAffiliation = "Kerala University of Health Sciences (KUHS)",
            campusLocation = "Dhanwanthari Nagar, Thiruvananthapuram",
            isAyush = true
        ),
        CollegeInfo(
            id = "podar_ayurved_mumbai",
            name = "R.A. Podar Ayurved Medical College, Worli, Mumbai",
            shortName = "Podar Ayurved College Mumbai",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda)",
            state = "Maharashtra",
            universityAffiliation = "Maharashtra University of Health Sciences (MUHS)",
            campusLocation = "Dr. Annie Besant Road, Worli, Mumbai",
            isAyush = true
        ),
        CollegeInfo(
            id = "gach_patna",
            name = "Government Ayurvedic College & Hospital, Kadamkuan, Patna",
            shortName = "Govt Ayurvedic College Patna",
            category = CollegeCategory.AYUSH,
            stream = "BAMS (Ayurveda)",
            state = "Bihar",
            universityAffiliation = "Aryabhatta Knowledge University (AKU)",
            campusLocation = "Kadamkuan, Patna, Bihar",
            isAyush = true
        ),

        // --- HOMEOPATHY (BHMS) ---
        CollegeInfo(
            id = "nih_kolkata",
            name = "National Institute of Homoeopathy (NIH), Kolkata",
            shortName = "NIH Kolkata",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy) / MD (Homoeopathy)",
            state = "West Bengal",
            universityAffiliation = "West Bengal University of Health Sciences (WBUHS)",
            campusLocation = "Block-GE, Sector-III, Salt Lake, Kolkata",
            isAyush = true
        ),
        CollegeInfo(
            id = "nhmc_delhi",
            name = "Nehru Homoeopathic Medical College and Hospital, New Delhi",
            shortName = "Nehru Homoeopathic Delhi",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy)",
            state = "Delhi",
            universityAffiliation = "University of Delhi (DU)",
            campusLocation = "B-Block, Defence Colony, New Delhi",
            isAyush = true
        ),
        CollegeInfo(
            id = "ghmc_tvm",
            name = "Government Homoeopathic Medical College, Thiruvananthapuram",
            shortName = "Govt Homoeopathic College TVM",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy) / MD (Homoeopathy)",
            state = "Kerala",
            universityAffiliation = "Kerala University of Health Sciences (KUHS)",
            campusLocation = "Iranimuttam, Manacaud, Thiruvananthapuram",
            isAyush = true
        ),
        CollegeInfo(
            id = "national_homoeopathic_lucknow",
            name = "National Homoeopathic Medical College and Hospital, Lucknow",
            shortName = "National Homoeopathic Lucknow",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy)",
            state = "Uttar Pradesh",
            universityAffiliation = "Atal Bihari Vajpayee Medical University (ABVMU)",
            campusLocation = "1, Cantonment Road, Lucknow",
            isAyush = true
        ),
        CollegeInfo(
            id = "bakson_homoeopathic",
            name = "Bakson Homoeopathic Medical College and Hospital, Greater Noida",
            shortName = "Bakson Homoeopathic College",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy)",
            state = "Uttar Pradesh",
            universityAffiliation = "Atal Bihari Vajpayee Medical University (ABVMU)",
            campusLocation = "Knowledge Park-I, Greater Noida",
            isAyush = true
        ),
        CollegeInfo(
            id = "father_muller_homoeopathic",
            name = "Father Muller Homoeopathic Medical College & Hospital, Mangaluru",
            shortName = "Father Muller Homoeopathic",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy)",
            state = "Karnataka",
            universityAffiliation = "Rajiv Gandhi University of Health Sciences (RGUHS)",
            campusLocation = "Deralakatte, Mangaluru, Karnataka",
            isAyush = true
        ),
        CollegeInfo(
            id = "calcutta_homoeopathic",
            name = "The Calcutta Homoeopathic Medical College and Hospital",
            shortName = "Calcutta Homoeopathic College",
            category = CollegeCategory.AYUSH,
            stream = "BHMS (Homoeopathy)",
            state = "West Bengal",
            universityAffiliation = "West Bengal University of Health Sciences (WBUHS)",
            campusLocation = "Acharya Prafulla Chandra Road, Kolkata",
            isAyush = true
        ),

        // --- UNANI (BUMS) ---
        CollegeInfo(
            id = "nium_bangalore",
            name = "National Institute of Unani Medicine (NIUM), Bengaluru",
            shortName = "NIUM Bengaluru",
            category = CollegeCategory.AYUSH,
            stream = "BUMS / MD (Unani)",
            state = "Karnataka",
            universityAffiliation = "Rajiv Gandhi University of Health Sciences (RGUHS)",
            campusLocation = "Kottigepalya, Magadi Main Road, Bengaluru",
            isAyush = true
        ),
        CollegeInfo(
            id = "ak_tibbiya_amu",
            name = "Ajmal Khan Tibbiya College (AKTC AMU), Aligarh",
            shortName = "AK Tibbiya College AMU",
            category = CollegeCategory.AYUSH,
            stream = "BUMS / MD (Unani)",
            state = "Uttar Pradesh",
            universityAffiliation = "Aligarh Muslim University (Central University)",
            campusLocation = "Medical Road, AMU, Aligarh",
            isAyush = true
        ),
        CollegeInfo(
            id = "tibbia_unani_delhi",
            name = "Ayurvedic and Unani Tibbia College (Unani Faculty), Karol Bagh",
            shortName = "A&U Tibbia College (Unani)",
            category = CollegeCategory.AYUSH,
            stream = "BUMS (Unani)",
            state = "Delhi",
            universityAffiliation = "University of Delhi (DU)",
            campusLocation = "Karol Bagh, New Delhi",
            isAyush = true
        ),
        CollegeInfo(
            id = "nizamia_tibbi_hyderabad",
            name = "Government Nizamia Tibbi College, Hyderabad",
            shortName = "Govt Nizamia Tibbi Hyderabad",
            category = CollegeCategory.AYUSH,
            stream = "BUMS (Unani)",
            state = "Telangana",
            universityAffiliation = "Kaloji Narayana Rao University of Health Sciences (KNRUHS)",
            campusLocation = "Charminar, Hyderabad",
            isAyush = true
        ),
        CollegeInfo(
            id = "state_unani_prayagraj",
            name = "State Unani Medical College & Himmat Bahadur Hospital, Prayagraj",
            shortName = "State Unani College Prayagraj",
            category = CollegeCategory.AYUSH,
            stream = "BUMS (Unani)",
            state = "Uttar Pradesh",
            universityAffiliation = "Atal Bihari Vajpayee Medical University (ABVMU)",
            campusLocation = "Gaughat, Prayagraj, UP",
            isAyush = true
        ),

        // --- SIDDHA (BSMS) ---
        CollegeInfo(
            id = "nis_chennai",
            name = "National Institute of Siddha (NIS), Tambaram, Chennai",
            shortName = "National Institute of Siddha",
            category = CollegeCategory.AYUSH,
            stream = "BSMS / MD (Siddha)",
            state = "Tamil Nadu",
            universityAffiliation = "The Tamil Nadu Dr. M.G.R. Medical University",
            campusLocation = "Grand Southern Trunk Rd, Tambaram Sanatorium, Chennai",
            isAyush = true
        ),
        CollegeInfo(
            id = "gsmc_palayamkottai",
            name = "Government Siddha Medical College, Palayamkottai, Tirunelveli",
            shortName = "Govt Siddha College Palayamkottai",
            category = CollegeCategory.AYUSH,
            stream = "BSMS (Siddha)",
            state = "Tamil Nadu",
            universityAffiliation = "The Tamil Nadu Dr. M.G.R. Medical University",
            campusLocation = "Palayamkottai, Tirunelveli, Tamil Nadu",
            isAyush = true
        ),

        // --- YOGA & NATUROPATHY (BNYS) ---
        CollegeInfo(
            id = "mdniy_delhi",
            name = "Morarji Desai National Institute of Yoga (MDNIY), New Delhi",
            shortName = "MDNIY New Delhi",
            category = CollegeCategory.AYUSH,
            stream = "BNYS / Yoga Sciences",
            state = "Delhi",
            universityAffiliation = "Ministry of AYUSH Autonomous Institute",
            campusLocation = "68, Ashoka Road, New Delhi",
            isAyush = true
        ),
        CollegeInfo(
            id = "sdm_naturopathy",
            name = "SDM College of Naturopathy and Yogic Sciences, Ujire",
            shortName = "SDM Naturopathy College",
            category = CollegeCategory.AYUSH,
            stream = "BNYS (Naturopathy & Yoga)",
            state = "Karnataka",
            universityAffiliation = "Rajiv Gandhi University of Health Sciences (RGUHS)",
            campusLocation = "Ujire, Belthangady, Dakshina Kannada, Karnataka",
            isAyush = true
        ),

        // ==========================================
        // 4. GENERAL MEDICAL / CUSTOM OPTION
        // ==========================================
        CollegeInfo(
            id = "other_custom_institute",
            name = "Other Medical / AYUSH Institute",
            shortName = "Other Institute",
            category = CollegeCategory.ALLOPATHIC,
            stream = "Medical / Healthcare",
            state = "India",
            universityAffiliation = "State Health Sciences University",
            campusLocation = "Main Campus"
        )
    )

    fun findCollege(idOrName: String): CollegeInfo? {
        val normalized = idOrName.trim().lowercase()
        return colleges.firstOrNull { 
            it.id.equals(normalized, ignoreCase = true) ||
            it.name.equals(normalized, ignoreCase = true) ||
            it.shortName.equals(normalized, ignoreCase = true)
        } ?: colleges.firstOrNull { 
            it.name.contains(normalized, ignoreCase = true) || 
            it.shortName.contains(normalized, ignoreCase = true) 
        }
    }

    fun filterColleges(query: String, category: CollegeCategory? = null, isAyushOnly: Boolean = false): List<CollegeInfo> {
        val q = query.trim().lowercase()
        return colleges.filter { college ->
            val matchesCategory = category == null || college.category == category
            val matchesAyush = !isAyushOnly || college.isAyush
            val matchesQuery = q.isBlank() ||
                    college.name.contains(q, ignoreCase = true) ||
                    college.shortName.contains(q, ignoreCase = true) ||
                    college.state.contains(q, ignoreCase = true) ||
                    college.stream.contains(q, ignoreCase = true) ||
                    college.universityAffiliation.contains(q, ignoreCase = true)
            matchesCategory && matchesAyush && matchesQuery
        }
    }
}
