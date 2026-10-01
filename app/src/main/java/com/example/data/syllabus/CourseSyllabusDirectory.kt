package com.example.data.syllabus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.MedicalCourse

/**
 * Subject metadata with standard curriculum name, icon, thematic color, and suggested topics/chapters.
 */
data class SyllabusSubject(
    val name: String,
    val shortName: String,
    val colorHex: String = "#4F46E5",
    val iconType: String = "book", // book, pharmacy, microscope, bone, heart, flask, scalpel, tooth, herb, cross
    val suggestedTopics: List<String> = emptyList()
)

data class YearCurriculum(
    val yearName: String,
    val yearOrder: Int,
    val subjects: List<SyllabusSubject>
)

data class CourseCurriculum(
    val course: MedicalCourse,
    val years: List<YearCurriculum>
)

/**
 * Authoritative medical syllabus directory covering all academic years of:
 * - BHMS (Homeopathy)
 * - MBBS (Allopathy)
 * - BDS (Dental)
 * - BAMS (Ayurveda)
 * - NURSING (B.Sc Nursing)
 * - PHARMACY (B.Pharm)
 */
object CourseSyllabusDirectory {

    private val curricula: Map<String, CourseCurriculum> = listOf(
        // ==========================================
        // 1. BHMS (Homoeopathic Medicine & Surgery)
        // ==========================================
        CourseCurriculum(
            course = MedicalCourse.BHMS,
            years = listOf(
                YearCurriculum(
                    yearName = "1st Year",
                    yearOrder = 1,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Homeopathic Pharmacy",
                            shortName = "Pharmacy",
                            colorHex = "#10B981",
                            iconType = "pharmacy",
                            suggestedTopics = listOf(
                                "Doctrine of Signatures",
                                "Sources of Homoeopathic Drugs (Vegetable, Animal, Mineral, Nosodes, Sarcodes, Imponderabilia)",
                                "Vehicles (Solid, Liquid, Semi-solid)",
                                "Preparation of Mother Tinctures & Solutions (Old & Modern Methods)",
                                "Potentisation / Dynamisation (Decimal, Centesimal, 50 Millesimal)",
                                "Homoeopathic Pharmacopoeias (HPI, HPUS, GHP)",
                                "Posology & Concept of Minimum Dose",
                                "Drug Proving on Healthy Volunteers",
                                "Prescription Writing & Dispensing",
                                "Standardisation of Homoeopathic Drugs"
                            )
                        ),
                        SyllabusSubject(
                            name = "Organon of Medicine & Philosophy",
                            shortName = "Organon",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf(
                                "Aphorisms 1 - 29 (Mission of Physician, Highest Ideal of Cure)",
                                "Vital Force & Dynamis in Health, Disease and Cure",
                                "Law of Similars (Similia Similibus Curentur)",
                                "Totality of Symptoms",
                                "Individualisation in Homoeopathy",
                                "Hahnemannian Classification of Diseases",
                                "Drug Proving Methodology",
                                "Idiosyncrasy & Susceptibility",
                                "Dynamic Cause of Disease"
                            )
                        ),
                        SyllabusSubject(
                            name = "Homoeopathic Materia Medica",
                            shortName = "Materia Medica",
                            colorHex = "#EC4899",
                            iconType = "flask",
                            suggestedTopics = listOf(
                                "Introduction & Sources of Homoeopathic Materia Medica",
                                "Nature & Limitations of Materia Medica",
                                "Aconitum Napellus",
                                "Arnica Montana",
                                "Belladonna",
                                "Bryonia Alba",
                                "Chamomilla",
                                "Cinchona Officinalis (China)",
                                "Dulcamara",
                                "Nux Vomica (Introductory)",
                                "Pulsatilla Nigricans"
                            )
                        ),
                        SyllabusSubject(
                            name = "Human Anatomy, Histology & Embryology",
                            shortName = "Anatomy",
                            colorHex = "#F59E0B",
                            iconType = "bone",
                            suggestedTopics = listOf(
                                "Upper Limb & Axilla",
                                "Thorax & Heart, Lungs",
                                "Abdomen & Pelvis",
                                "Lower Limb & Joints",
                                "Head, Neck & Brain",
                                "General Histology & Epithelial Tissues",
                                "General Embryology & Germ Layers"
                            )
                        ),
                        SyllabusSubject(
                            name = "Human Physiology & Biochemistry",
                            shortName = "Physiology",
                            colorHex = "#06B6D4",
                            iconType = "heart",
                            suggestedTopics = listOf(
                                "General Physiology & Cell Membrane Transport",
                                "Blood, Plasma Proteins & Coagulation",
                                "Cardiovascular System (CVS) & Cardiac Cycle",
                                "Respiratory Mechanics & Gas Exchange",
                                "Digestive System & Enzymes",
                                "Renal Function & Nephron Dynamics",
                                "Endocrine System & Hormones",
                                "Elementary Biochemistry, Carbohydrates & Proteins"
                            )
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "2nd Year",
                    yearOrder = 2,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Pathology, Microbiology & Parasitology",
                            shortName = "Pathology",
                            colorHex = "#EF4444",
                            iconType = "microscope",
                            suggestedTopics = listOf(
                                "Cell Injury, Necrosis & Apoptosis",
                                "Acute & Chronic Inflammation, Repair",
                                "Hemodynamic Disorders, Shock, Thrombosis",
                                "Neoplasia (Benign & Malignant)",
                                "General Bacteriology & Gram Staining",
                                "Immunology & Hypersensitivity",
                                "Systemic Bacteriology (Streptococcus, Staphylococcus)",
                                "Virology & Hepatitis, HIV",
                                "Medical Parasitology (Malaria, Amoebiasis, Helminths)"
                            )
                        ),
                        SyllabusSubject(
                            name = "Forensic Medicine & Toxicology (FMT)",
                            shortName = "FMT",
                            colorHex = "#8B5CF6",
                            iconType = "scalpel",
                            suggestedTopics = listOf(
                                "Medical Jurisprudence, Ethics & Consent",
                                "Thanatology, Post-mortem Changes & Autopsy",
                                "Mechanical Injuries & Wound Ballistics",
                                "Death due to Asphyxia (Hanging, Strangulation, Drowning)",
                                "Sexual Jurisprudence & Rape Examination",
                                "General Principles of Toxicology",
                                "Corrosive & Heavy Metal Poisons (Arsenic, Lead)",
                                "Snake Venom & Plant Poisons (Datura, Nux Vomica)"
                            )
                        ),
                        SyllabusSubject(
                            name = "Organon of Medicine & Homoeopathic Philosophy",
                            shortName = "Organon",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf(
                                "Aphorisms 30 - 70 (Action of Medicines & Disease)",
                                "Classification of Diseases (Aphorisms 71 - 82)",
                                "Case Taking (Aphorisms 83 - 104)",
                                "Symptomatology (Characteristic, Common, General, Particular)",
                                "Evaluation of Symptoms (Kent, Boenninghausen, Hahnemann)",
                                "Primary and Secondary Action of Medicines"
                            )
                        ),
                        SyllabusSubject(
                            name = "Homoeopathic Materia Medica",
                            shortName = "Materia Medica",
                            colorHex = "#EC4899",
                            iconType = "flask",
                            suggestedTopics = listOf(
                                "Arsenicum Album",
                                "Calcarea Carbonica",
                                "Colocynthis",
                                "Hepar Sulphuris",
                                "Lycopodium Clavatum",
                                "Natrum Muriaticum",
                                "Rhus Toxicodendron",
                                "Sulphur",
                                "Thuja Occidentalis",
                                "Veratrum Album"
                            )
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "3rd Year",
                    yearOrder = 3,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Surgery, ENT & Ophthalmology with Therapeutics",
                            shortName = "Surgery",
                            colorHex = "#EF4444",
                            iconType = "scalpel",
                            suggestedTopics = listOf(
                                "Wounds, Ulcers, Sinus, Fistula & Gangrene",
                                "Shock, Haemorrhage & Blood Transfusion",
                                "Tumours, Cysts & Surgical Oncology",
                                "Fractures, Dislocations & Orthopaedics",
                                "Diseases of Ear, Nose & Throat (ENT)",
                                "Diseases of Eye & Conjunctiva, Cataract",
                                "Homoeopathic Surgical Therapeutics (Calendula, Hypericum, Staphysagria)"
                            )
                        ),
                        SyllabusSubject(
                            name = "Obstetrics & Gynaecology with Therapeutics",
                            shortName = "Gynae & Obs",
                            colorHex = "#F43F5E",
                            iconType = "cross",
                            suggestedTopics = listOf(
                                "Normal Pregnancy, Antenatal Care & Foetal Development",
                                "Physiology & Stages of Normal Labour",
                                "Abnormal Labour & Post-Partum Haemorrhage (PPH)",
                                "Menstrual Disorders (Amenorrhoea, Dysmenorrhoea, Menorrhagia)",
                                "Infertility & Fibroids",
                                "Homoeopathic Remedies in Pregnancy & Labour (Caulophyllum, Actaea Racemosa)"
                            )
                        ),
                        SyllabusSubject(
                            name = "Organon of Medicine & Chronic Diseases",
                            shortName = "Organon",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf(
                                "Chronic Diseases & Theory of Chronic Miasms",
                                "Psora: Origin, Manifestations & Therapeutics",
                                "Sycosis: Manifestations & Gonorrhoeal Diathesis",
                                "Syphilis: Destructive Manifestations",
                                "Mental Diseases (Aphorisms 210 - 230)",
                                "Intermittent & Alternating Diseases"
                            )
                        ),
                        SyllabusSubject(
                            name = "Homoeopathic Materia Medica",
                            shortName = "Materia Medica",
                            colorHex = "#EC4899",
                            iconType = "flask",
                            suggestedTopics = listOf(
                                "Apis Mellifica",
                                "Baryta Carbonica",
                                "Carbo Vegetabilis",
                                "Graphites",
                                "Kali Bichromicum & Kali Carbonicum",
                                "Lachesis Mutus",
                                "Mercurius Solubilis",
                                "Phosphorus",
                                "Sepia Officinalis",
                                "Silicea"
                            )
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "4th Year / Final Year",
                    yearOrder = 4,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Practice of Medicine & Therapeutics",
                            shortName = "Medicine",
                            colorHex = "#3B82F6",
                            iconType = "heart",
                            suggestedTopics = listOf(
                                "Cardiovascular System Disorders (HTN, IHD, Heart Failure)",
                                "Respiratory Disorders (Pneumonia, Bronchial Asthma, COPD)",
                                "Gastrointestinal Disorders (Gastritis, Peptic Ulcer, Cirrhosis)",
                                "Neurology (Stroke, Epilepsy, Migraine)",
                                "Endocrinology (Diabetes Mellitus, Thyroid Disorders)",
                                "Infectious Diseases (Typhoid, Dengue, Tuberculosis)",
                                "Dermatology & Skin Eruptions"
                            )
                        ),
                        SyllabusSubject(
                            name = "Repertory & Case Taking",
                            shortName = "Repertory",
                            colorHex = "#14B8A6",
                            iconType = "book",
                            suggestedTopics = listOf(
                                "History & Development of Homoeopathic Repertories",
                                "Kent's Repertory: Philosophy, Plan & Construction",
                                "Boenninghausen's Therapeutic Pocket Book (BTPB)",
                                "Boger Boenninghausen's Characteristics & Repertory (BBCR)",
                                "Card Repertories & Computerized Repertory Software (RADAR)",
                                "Technique of Repertorisation & Rubric Selection",
                                "Evaluation of Symptoms & Repertorial Analysis"
                            )
                        ),
                        SyllabusSubject(
                            name = "Homoeopathic Materia Medica",
                            shortName = "Materia Medica",
                            colorHex = "#EC4899",
                            iconType = "flask",
                            suggestedTopics = listOf(
                                "Nosodes: Psorinum, Medorrhinum, Syphilinum, Tuberculinum",
                                "Ophidia Group (Lachesis, Crotalus, Naja)",
                                "Spiders & Insects (Tarentula, Cantharis)",
                                "Aurum Metallicum & Argentum Nitricum",
                                "Zincum Metallicum & Causticum",
                                "Comparative Study of Polycrest Remedies"
                            )
                        ),
                        SyllabusSubject(
                            name = "Organon of Medicine",
                            shortName = "Organon",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf(
                                "Review of Aphorisms & Chronic Miasms",
                                "Constitutional Prescribing & Second Prescription",
                                "Kent's 12 Observations",
                                "Prognosis after Remedy Administration",
                                "Obstacles to Cure & Palliation"
                            )
                        ),
                        SyllabusSubject(
                            name = "Community Medicine (PSM)",
                            shortName = "PSM",
                            colorHex = "#F59E0B",
                            iconType = "cross",
                            suggestedTopics = listOf(
                                "Concept of Health & Disease, Primary Health Care",
                                "Epidemiology: Principles, Methods & Disease Surveillance",
                                "Communicable & Non-Communicable Diseases Prevention",
                                "Demography, Family Planning & Maternal Child Health (MCH)",
                                "Nutrition & Nutritional Deficiencies in India",
                                "Environmental Health, Water Purification & Waste Disposal",
                                "National Health Programmes in India"
                            )
                        )
                    )
                )
            )
        ),

        // ==========================================
        // 2. MBBS (NMC Competency-Based Medical Education)
        // ==========================================
        CourseCurriculum(
            course = MedicalCourse.MBBS,
            years = listOf(
                YearCurriculum(
                    yearName = "1st Year",
                    yearOrder = 1,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Human Anatomy",
                            shortName = "Anatomy",
                            colorHex = "#F59E0B",
                            iconType = "bone",
                            suggestedTopics = listOf(
                                "Gross Anatomy: Upper Limb & Brachial Plexus",
                                "Thorax: Mediastinum, Heart & Coronary Circulation, Lungs",
                                "Abdomen & Pelvis: Inguinal Canal, Peritoneum, Viscera",
                                "Lower Limb: Femoral Triangle, Gluteal Region, Knee Joint",
                                "Head, Neck & Brain: Cranial Nerves, Circle of Willis",
                                "General & Systemic Histology",
                                "General & Systemic Embryology",
                                "Genetics & Karyotyping"
                            )
                        ),
                        SyllabusSubject(
                            name = "Human Physiology",
                            shortName = "Physiology",
                            colorHex = "#06B6D4",
                            iconType = "heart",
                            suggestedTopics = listOf(
                                "General Physiology, Body Fluid Compartments & Homeostasis",
                                "Hematology, Erythropoiesis, Blood Groups & Hemostasis",
                                "Cardiovascular System: Cardiac Cycle, ECG, Blood Pressure",
                                "Respiratory System: Mechanics, Gas Transport, Regulation",
                                "Renal Physiology: GFR, Counter-current Mechanism, Acid-Base",
                                "Endocrine System & Pituitary-Thyroid-Adrenal Axis",
                                "Central Nervous System, Sensory & Motor Systems, Reflexes"
                            )
                        ),
                        SyllabusSubject(
                            name = "Biochemistry",
                            shortName = "Biochemistry",
                            colorHex = "#8B5CF6",
                            iconType = "flask",
                            suggestedTopics = listOf(
                                "Enzymes: Kinetics, Inhibition & Diagnostic Significance",
                                "Carbohydrate Metabolism: Glycolysis, TCA Cycle, Glycogenesis",
                                "Lipid Metabolism: Fatty Acid Oxidation, Lipoproteins, Ketogenesis",
                                "Protein & Amino Acid Metabolism: Urea Cycle, Inborn Errors",
                                "Molecular Biology: DNA Replication, Transcription, Translation",
                                "Nutrition, Vitamins & Minerals",
                                "Organ Function Tests (LFT, KFT)"
                            )
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "2nd Year",
                    yearOrder = 2,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Pathology",
                            shortName = "Pathology",
                            colorHex = "#EF4444",
                            iconType = "microscope",
                            suggestedTopics = listOf(
                                "Cell Injury, Necrosis, Apoptosis & Cellular Adaptations",
                                "Inflammation: Acute, Chronic & Granulomatous",
                                "Hemodynamic Disorders, Edema, Thrombosis, Embolism, Shock",
                                "Neoplasia: Carcinogenesis, Molecular Basis, Tumor Markers",
                                "Hematology: Anemias, Leukemias, Coagulation Disorders",
                                "Systemic Pathology: Atherosclerosis, IHD, Cirrhosis, Glomerulonephritis"
                            )
                        ),
                        SyllabusSubject(
                            name = "Pharmacology",
                            shortName = "Pharmacology",
                            colorHex = "#10B981",
                            iconType = "pharmacy",
                            suggestedTopics = listOf(
                                "General Pharmacology: Pharmacokinetics & Pharmacodynamics",
                                "Autonomic Nervous System (Cholinergic & Adrenergic Drugs)",
                                "Cardiovascular Drugs: Antihypertensives, Antianginal, Diuretics",
                                "Central Nervous System: Sedatives, Antiepileptics, Antidepressants",
                                "Antimicrobial Agents: Penicillins, Cephalosporins, Quinolones",
                                "Chemotherapy & Anticancer Drugs",
                                "Endocrine Pharmacology: Antidiabetics, Corticosteroids"
                            )
                        ),
                        SyllabusSubject(
                            name = "Microbiology",
                            shortName = "Microbiology",
                            colorHex = "#EC4899",
                            iconType = "microscope",
                            suggestedTopics = listOf(
                                "General Bacteriology & Bacterial Genetics",
                                "Immunology: Innate & Adaptive Immunity, Hypersensitivity, Vaccines",
                                "Systemic Bacteriology: Mycobacteria, Enterobacteriaceae, Spores",
                                "Virology: Hepatitis Viruses, HIV, Arboviruses, Influenza",
                                "Parasitology: Entamoeba, Plasmodium, Leishmania, Cestodes",
                                "Mycology: Superficial & Systemic Fungal Infections",
                                "Hospital Infection Control & Biomedical Waste"
                            )
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "3rd Year",
                    yearOrder = 3,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Community Medicine (PSM)",
                            shortName = "PSM",
                            colorHex = "#F59E0B",
                            iconType = "cross",
                            suggestedTopics = listOf(
                                "Epidemiology: Study Designs, Cohort, Case-Control, RCT",
                                "Biostatistics & Health Information Systems",
                                "Epidemiology of Communicable & Non-Communicable Diseases",
                                "Nutrition & Nutritional Surveillance",
                                "Reproductive, Maternal, Newborn, Child Health (RMNCH+A)",
                                "Occupational Health & Environmental Sanitation",
                                "National Health Mission (NHM) & Health Programs"
                            )
                        ),
                        SyllabusSubject(
                            name = "Forensic Medicine & Toxicology (FMT)",
                            shortName = "FMT",
                            colorHex = "#6366F1",
                            iconType = "scalpel",
                            suggestedTopics = listOf(
                                "Legal Procedures, Courts & Expert Witness Testimony",
                                "Thanatology: Signs of Death, Post-Mortem Interval",
                                "Mechanical Injuries, Firearm Wounds & Blast Injuries",
                                "Thermal Injuries & Asphyxial Deaths",
                                "Sexual Offences & Medico-legal Autopsy",
                                "General & Clinical Toxicology",
                                "Agricultural & Corrosive Poisons"
                            )
                        ),
                        SyllabusSubject(
                            name = "Ophthalmology",
                            shortName = "Ophtha",
                            colorHex = "#06B6D4",
                            iconType = "cross",
                            suggestedTopics = listOf(
                                "Conjunctiva & Cornea: Keratitis, Ulcers, Trachoma",
                                "Cataract: Types, Pathogenesis & Surgical Techniques (Phaco)",
                                "Glaucoma: Primary Open-Angle & Angle-Closure Glaucoma",
                                "Retina: Diabetic Retinopathy, Retinal Detachment, ARMD",
                                "Uvea, Strabismus & Refractive Errors (Myopia, Hyperopia)"
                            )
                        ),
                        SyllabusSubject(
                            name = "Otorhinolaryngology (ENT)",
                            shortName = "ENT",
                            colorHex = "#14B8A6",
                            iconType = "scalpel",
                            suggestedTopics = listOf(
                                "Ear: Otitis Media (ASOM, CSOM), Cholesteatoma, Otosclerosis",
                                "Hearing Loss & Audiometry, Vestibular Disorders & Vertigo",
                                "Nose: Deviated Nasal Septum (DNS), Sinusitis, Epistaxis, Polyps",
                                "Pharynx: Tonsillitis, Adenoids, Pharyngitis",
                                "Larynx: Stridor, Vocal Cord Nodules, Carcinoma Larynx"
                            )
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "4th Year / Final Year",
                    yearOrder = 4,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "General Medicine",
                            shortName = "Medicine",
                            colorHex = "#3B82F6",
                            iconType = "heart",
                            suggestedTopics = listOf(
                                "Cardiology: CAD, ACS, Valvular Heart Diseases, Heart Failure",
                                "Pulmonology: Pneumonia, Pulmonary TB, COPD, Asthma",
                                "Gastroenterology: GERD, Peptic Ulcers, Cirrhosis & Complications",
                                "Neurology: Stroke Management, Meningitis, Epilepsy, Neuropathy",
                                "Nephrology: Acute Kidney Injury, Chronic Kidney Disease",
                                "Endocrinology: Diabetes Mellitus, Metabolic Syndrome, Thyroid",
                                "Infectious Diseases: Malaria, Dengue, Sepsis & Septic Shock",
                                "Dermatology & Psychiatry Essentials"
                            )
                        ),
                        SyllabusSubject(
                            name = "General Surgery & Orthopaedics",
                            shortName = "Surgery",
                            colorHex = "#EF4444",
                            iconType = "scalpel",
                            suggestedTopics = listOf(
                                "Metabolic Response to Injury, Shock & Fluid Management",
                                "Wound Healing, Surgical Site Infections, Burns",
                                "Thyroid & Parathyroid Disorders, Breast Carcinoma",
                                "Hernias: Inguinal, Femoral, Incisional, Umbilical",
                                "Acute Abdomen: Appendicitis, Perforation, Intestinal Obstruction",
                                "Hepatobiliary: Cholecystitis, Cholelithiasis, Pancreatitis",
                                "Orthopaedics: Fractures, Dislocations, Bone Tumours",
                                "Principles of Anaesthesia & Critical Care"
                            )
                        ),
                        SyllabusSubject(
                            name = "Obstetrics & Gynaecology (OBG)",
                            shortName = "OBG",
                            colorHex = "#F43F5E",
                            iconType = "cross",
                            suggestedTopics = listOf(
                                "Antenatal Care, High-Risk Pregnancy (Preeclampsia, GDM)",
                                "Normal & Abnormal Labour, Partograph Monitoring",
                                "Post-Partum Haemorrhage (PPH) & Obstetric Emergencies",
                                "Abnormal Uterine Bleeding (AUB) & Fibroids",
                                "Cervical Cancer Screening (Pap Smear) & Malignancies",
                                "Infertility Evaluation, Contraception & MTP"
                            )
                        ),
                        SyllabusSubject(
                            name = "Paediatrics",
                            shortName = "Paediatrics",
                            colorHex = "#8B5CF6",
                            iconType = "heart",
                            suggestedTopics = listOf(
                                "Normal Growth, Development Milestones & Developmental Delay",
                                "Neonatal Resuscitation, Care of Normal & Low Birth Weight Newborn",
                                "Infant Feeding, Protein-Energy Malnutrition (PEM), Rickets",
                                "National Immunization Schedule & Vaccines",
                                "Common Childhood Infections (ARI, Diarrhoeal Diseases)",
                                "Pediatric Emergencies (Convulsions, Shock, Dehydration)"
                            )
                        )
                    )
                )
            )
        ),

        // ==========================================
        // 3. BDS (Bachelor of Dental Surgery)
        // ==========================================
        CourseCurriculum(
            course = MedicalCourse.BDS,
            years = listOf(
                YearCurriculum(
                    yearName = "1st Year",
                    yearOrder = 1,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "General Human Anatomy & Histology",
                            shortName = "Gen Anatomy",
                            colorHex = "#F59E0B",
                            iconType = "bone",
                            suggestedTopics = listOf("Head and Neck Anatomy", "Cranial Nerves", "Facial Arteries", "General Histology", "Embryology")
                        ),
                        SyllabusSubject(
                            name = "General Human Physiology & Biochemistry",
                            shortName = "Physio & Biochem",
                            colorHex = "#06B6D4",
                            iconType = "heart",
                            suggestedTopics = listOf("Blood & Circulation", "Salivary Secretion", "Carbohydrate & Protein Metabolism", "Vitamins & Calcium Metabolism")
                        ),
                        SyllabusSubject(
                            name = "Dental Anatomy, Embryology & Oral Histology",
                            shortName = "Dental Anatomy",
                            colorHex = "#10B981",
                            iconType = "tooth",
                            suggestedTopics = listOf("Tooth Morphology & Numbering", "Enamel & Dentin Histology", "Pulp & Periodontal Ligament", "Tooth Eruption & Shedding")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "2nd Year",
                    yearOrder = 2,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "General Pathology & Microbiology",
                            shortName = "Path & Micro",
                            colorHex = "#EF4444",
                            iconType = "microscope",
                            suggestedTopics = listOf("Inflammation & Healing", "Oral Flora & Cariogenic Bacteria", "Sterilization & Disinfection", "Immunity")
                        ),
                        SyllabusSubject(
                            name = "General & Dental Pharmacology",
                            shortName = "Pharmacology",
                            colorHex = "#6366F1",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("Local Anaesthetics in Dentistry", "Analgesics & NSAIDs", "Antibiotics in Dental Practice", "Emergency Drugs")
                        ),
                        SyllabusSubject(
                            name = "Dental Materials",
                            shortName = "Dental Materials",
                            colorHex = "#8B5CF6",
                            iconType = "flask",
                            suggestedTopics = listOf("Impression Materials (Alginate, Elastomers)", "Dental Gypsum & Cements", "Composite Resins & Amalgam", "Dental Ceramics & Alloys")
                        ),
                        SyllabusSubject(
                            name = "Preclinical Prosthodontics & Conservative Dentistry",
                            shortName = "Preclinical",
                            colorHex = "#14B8A6",
                            iconType = "tooth",
                            suggestedTopics = listOf("Complete Denture Fabrication", "Tooth Preparation & Cavity Design", "Restorative Exercises on Typhodont")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "3rd Year",
                    yearOrder = 3,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "General Medicine",
                            shortName = "Medicine",
                            colorHex = "#3B82F6",
                            iconType = "heart",
                            suggestedTopics = listOf("Cardiovascular Diseases & Bleeding Disorders", "Infective Endocarditis Prophylaxis", "Diabetes & Dental Complications")
                        ),
                        SyllabusSubject(
                            name = "General Surgery",
                            shortName = "Surgery",
                            colorHex = "#EF4444",
                            iconType = "scalpel",
                            suggestedTopics = listOf("Wounds & Surgical Infections", "Diseases of Salivary Glands", "Cysts & Tumours of Head & Neck", "Tracheostomy")
                        ),
                        SyllabusSubject(
                            name = "Oral Pathology & Oral Microbiology",
                            shortName = "Oral Pathology",
                            colorHex = "#EC4899",
                            iconType = "microscope",
                            suggestedTopics = listOf("Dental Caries & Pulpal Diseases", "Odontogenic Cysts & Tumours", "Oral Premalignant Lesions (Leukoplakia)", "Oral Squamous Cell Carcinoma")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "4th Year / Final Year",
                    yearOrder = 4,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Oral Medicine & Radiology",
                            shortName = "OMR",
                            colorHex = "#6366F1",
                            iconType = "cross",
                            suggestedTopics = listOf("Diagnosis of Oral Mucosal Lesions", "Intraoral & Extraoral Radiographs (OPG, CBCT)", "Radiographic Interpretation")
                        ),
                        SyllabusSubject(
                            name = "Oral & Maxillofacial Surgery",
                            shortName = "OMFS",
                            colorHex = "#EF4444",
                            iconType = "scalpel",
                            suggestedTopics = listOf("Tooth Extraction Techniques", "Impacted Mandibular Third Molars", "Maxillofacial Trauma & Fractures", "Cleft Lip & Palate")
                        ),
                        SyllabusSubject(
                            name = "Conservative Dentistry & Endodontics",
                            shortName = "Endo",
                            colorHex = "#10B981",
                            iconType = "tooth",
                            suggestedTopics = listOf("Root Canal Treatment (RCT)", "Pulp Therapy", "Aesthetic Veneers & Inlays", "Endodontic Emergencies")
                        ),
                        SyllabusSubject(
                            name = "Periodontology",
                            shortName = "Perio",
                            colorHex = "#F59E0B",
                            iconType = "tooth",
                            suggestedTopics = listOf("Gingivitis & Periodontitis Pathogenesis", "Scaling & Root Planing", "Periodontal Flap Surgeries", "Dental Implants")
                        ),
                        SyllabusSubject(
                            name = "Prosthodontics & Crown & Bridge",
                            shortName = "Prostho",
                            colorHex = "#8B5CF6",
                            iconType = "tooth",
                            suggestedTopics = listOf("Complete & Partial Removable Dentures", "Fixed Partial Dentures (FPD)", "Occlusion & Articulators")
                        ),
                        SyllabusSubject(
                            name = "Orthodontics & Dentofacial Orthopaedics",
                            shortName = "Ortho",
                            colorHex = "#06B6D4",
                            iconType = "tooth",
                            suggestedTopics = listOf("Malocclusion Classification (Angle's)", "Cephalometrics", "Removable & Fixed Appliances", "Space Maintainers")
                        ),
                        SyllabusSubject(
                            name = "Paediatric & Preventive Dentistry",
                            shortName = "Pedo",
                            colorHex = "#EC4899",
                            iconType = "tooth",
                            suggestedTopics = listOf("Child Behaviour Management", "Pulpotomy & Pulpectomy in Deciduous Teeth", "Pit & Fissure Sealants")
                        ),
                        SyllabusSubject(
                            name = "Public Health Dentistry",
                            shortName = "PHD",
                            colorHex = "#14B8A6",
                            iconType = "cross",
                            suggestedTopics = listOf("Dental Epidemiology & Indices (DMFT)", "Fluorides & Dental Fluorosis", "School Dental Health Programs")
                        )
                    )
                )
            )
        ),

        // ==========================================
        // 4. BAMS (Ayurveda)
        // ==========================================
        CourseCurriculum(
            course = MedicalCourse.BAMS,
            years = listOf(
                YearCurriculum(
                    yearName = "1st Year",
                    yearOrder = 1,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Rachana Sharir (Human Anatomy)",
                            shortName = "Rachana",
                            colorHex = "#F59E0B",
                            iconType = "bone",
                            suggestedTopics = listOf("Garbha Sharir (Embryology)", "Asthi Sharir (Osteology)", "Sandhi & Snayu (Joints & Ligaments)", "Sira & Dhamani (Vessels)", "Marma Sharir")
                        ),
                        SyllabusSubject(
                            name = "Kriya Sharir (Physiology)",
                            shortName = "Kriya",
                            colorHex = "#06B6D4",
                            iconType = "heart",
                            suggestedTopics = listOf("Tridosha Siddhanta (Vata, Pitta, Kapha)", "Saptadhatu Vigyan (Rasa, Rakta, Mamsa...)", "Trimala Vigyan", "Agni & Prakriti Pariksha")
                        ),
                        SyllabusSubject(
                            name = "Padartha Vigyan Evam Ayurveda Itihas",
                            shortName = "Padartha",
                            colorHex = "#8B5CF6",
                            iconType = "book",
                            suggestedTopics = listOf("Dravya, Guna, Karma, Samanya, Vishesha, Samavaya", "Pramana Vigyan (Pratyaksha, Anumana, Aptopadesha)", "History of Ayurveda & Brihat Trayi")
                        ),
                        SyllabusSubject(
                            name = "Samhita Adhyayan 1 (Ashtanga Hridaya)",
                            shortName = "Samhita 1",
                            colorHex = "#10B981",
                            iconType = "book",
                            suggestedTopics = listOf("Sutra Sthana Chapters 1 - 15", "Dinacharya (Daily Routine)", "Ritucharya (Seasonal Routine)", "Roga Anutpadaniya Adhyaya")
                        ),
                        SyllabusSubject(
                            name = "Sanskrit",
                            shortName = "Sanskrit",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf("Grammar, Sandhi, Samasa", "Shloka Recitation & Translation of Classical Medical Texts")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "2nd Year",
                    yearOrder = 2,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Dravyaguna Vigyan (Ayurvedic Pharmacology)",
                            shortName = "Dravyaguna",
                            colorHex = "#10B981",
                            iconType = "herb",
                            suggestedTopics = listOf("Rasa, Guna, Virya, Vipaka, Prabhava", "Nighantu Parichaya", "Herbal Drugs: Ashwagandha, Tulsi, Guduchi, Haritaki", "Classification of Drugs")
                        ),
                        SyllabusSubject(
                            name = "Rasa Shastra Evam Bhaishajya Kalpana",
                            shortName = "Rasa Shastra",
                            colorHex = "#EC4899",
                            iconType = "flask",
                            suggestedTopics = listOf("Parada Shodhana & Samskara", "Bhasma Kalpana (Metals & Minerals)", "Pancha Vidha Kashaya Kalpana (Swarasa, Kalka, Kwatha...)", "Sneha Kalpana & Asava Arishta")
                        ),
                        SyllabusSubject(
                            name = "Roga Nidan Evam Vikriti Vigyan (Pathology)",
                            shortName = "Roga Nidan",
                            colorHex = "#EF4444",
                            iconType = "microscope",
                            suggestedTopics = listOf("Nidana Panchaka (Hetu, Linga, Aushadha)", "Srotas & Sroto Dushti", "Shat Kriya Kala", "Ashtavidha Pariksha (Nadi, Mutra, Mala...)")
                        ),
                        SyllabusSubject(
                            name = "Charaka Samhita (Purvardha)",
                            shortName = "Charaka 1",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf("Sutra Sthana, Nidana Sthana, Vimana Sthana, Sharira Sthana, Indriya Sthana")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "3rd Year",
                    yearOrder = 3,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Agada Tantra & Forensic Medicine",
                            shortName = "Agada Tantra",
                            colorHex = "#8B5CF6",
                            iconType = "scalpel",
                            suggestedTopics = listOf("Visha Varga & Classification of Poisons", "Sthavara & Jangama Visha", "Snake Bites & Keeta Damsha", "Upavisha & Medical Jurisprudence")
                        ),
                        SyllabusSubject(
                            name = "Swasthavritta & Yoga (Preventive Medicine)",
                            shortName = "Swasthavritta",
                            colorHex = "#14B8A6",
                            iconType = "cross",
                            suggestedTopics = listOf("Personal Hygiene, Sadvritta", "Yoga Asanas, Pranayama, Shatkarma", "Epidemiology & Environmental Health")
                        ),
                        SyllabusSubject(
                            name = "Prasuti Tantra Evam Stree Roga",
                            shortName = "Prasuti & Stree",
                            colorHex = "#F43F5E",
                            iconType = "cross",
                            suggestedTopics = listOf("Garbhadhana, Garbhini Paricharya", "Prasava (Normal & Abnormal Labour)", "Stree Roga (Yonivyapad, Artava Dushti)")
                        ),
                        SyllabusSubject(
                            name = "Kaumarbhritya (Pediatrics)",
                            shortName = "Kaumarbhritya",
                            colorHex = "#06B6D4",
                            iconType = "heart",
                            suggestedTopics = listOf("Navajata Shishu Paricharya", "Stanya & Dhatri (Breast milk & Nursing)", "Common Childhood Diseases in Ayurveda")
                        ),
                        SyllabusSubject(
                            name = "Charaka Samhita (Uttarardha)",
                            shortName = "Charaka 2",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf("Chikitsa Sthana Chapters 1 - 30", "Rasayana & Vajikarana Adhyaya")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "4th Year / Final Year",
                    yearOrder = 4,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Kayachikitsa (Internal Medicine)",
                            shortName = "Kayachikitsa",
                            colorHex = "#3B82F6",
                            iconType = "heart",
                            suggestedTopics = listOf("Jwara (Fever) Chikitsa", "Amavata & Sandhigata Vata", "Prameha (Diabetes) Chikitsa", "Tamaka Shwasa (Asthma)", "Pakshaghata (Hemiplegia)")
                        ),
                        SyllabusSubject(
                            name = "Shalya Tantra (Surgery)",
                            shortName = "Shalya",
                            colorHex = "#EF4444",
                            iconType = "scalpel",
                            suggestedTopics = listOf("Sushruta's Surgical Instruments (Yantra & Shastra)", "Kshara Sutra & Agnikarma", "Arsha (Hemorrhoids) & Bhagandara (Fistula)", "Fracture Management (Bhagna)")
                        ),
                        SyllabusSubject(
                            name = "Shalakya Tantra (ENT, Eye & Dentistry)",
                            shortName = "Shalakya",
                            colorHex = "#EC4899",
                            iconType = "cross",
                            suggestedTopics = listOf("Netra Roga (Eye Diseases & Kriyakalpa)", "Karna Roga (Ear Diseases)", "Nasa & Mukha Roga (Nose & Mouth Disorders)")
                        ),
                        SyllabusSubject(
                            name = "Panchakarma",
                            shortName = "Panchakarma",
                            colorHex = "#10B981",
                            iconType = "herb",
                            suggestedTopics = listOf("Purvakarma: Snehana & Swedana", "Vamana Karma (Therapeutic Emesis)", "Virechana Karma (Purgation)", "Basti Karma (Niruha & Anuvasana)", "Nasya & Raktamokshana")
                        ),
                        SyllabusSubject(
                            name = "Research Methodology & Medical Statistics",
                            shortName = "Research",
                            colorHex = "#F59E0B",
                            iconType = "book",
                            suggestedTopics = listOf("Types of Research Studies", "Clinical Trial Protocol in Ayurveda", "Sampling & Statistical Tests")
                        )
                    )
                )
            )
        ),

        // ==========================================
        // 5. NURSING (B.Sc Nursing - INC)
        // ==========================================
        CourseCurriculum(
            course = MedicalCourse.NURSING,
            years = listOf(
                YearCurriculum(
                    yearName = "1st Year",
                    yearOrder = 1,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Applied Anatomy & Applied Physiology",
                            shortName = "Anat & Physio",
                            colorHex = "#F59E0B",
                            iconType = "bone",
                            suggestedTopics = listOf("Skeletal & Muscular System", "Cardiorespiratory Systems", "Nervous System & Special Senses", "Excretory & Reproductive Systems")
                        ),
                        SyllabusSubject(
                            name = "Applied Biochemistry & Applied Nutrition",
                            shortName = "Nutrition",
                            colorHex = "#06B6D4",
                            iconType = "flask",
                            suggestedTopics = listOf("Macronutrients & Micronutrients", "Therapeutic Diets in Illness", "Nutritional Assessment", "Fluid & Electrolyte Balance")
                        ),
                        SyllabusSubject(
                            name = "Nursing Foundation I & II",
                            shortName = "Foundations",
                            colorHex = "#10B981",
                            iconType = "cross",
                            suggestedTopics = listOf("Nursing Process & Documentation", "Vital Signs Assessment", "Infection Prevention & Asepsis", "Medication Administration", "Patient Safety & Mobility")
                        ),
                        SyllabusSubject(
                            name = "Applied Psychology & Applied Sociology",
                            shortName = "Psych & Socio",
                            colorHex = "#8B5CF6",
                            iconType = "book",
                            suggestedTopics = listOf("Human Behaviour & Coping Mechanisms", "Stress & Crisis Intervention", "Social Structure & Health Disparities")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "2nd Year",
                    yearOrder = 2,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Applied Pharmacology & Pathology",
                            shortName = "Pharm & Path",
                            colorHex = "#EF4444",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("Pharmacotherapy in Common Illnesses", "Adverse Drug Reactions & Toxicity", "Clinical Pathology & Diagnostic Tests", "Genetics in Nursing")
                        ),
                        SyllabusSubject(
                            name = "Adult Health Nursing I (Medical Surgical I)",
                            shortName = "Med-Surg 1",
                            colorHex = "#3B82F6",
                            iconType = "heart",
                            suggestedTopics = listOf("Perioperative Nursing Care", "Care of Patients with Respiratory Disorders", "Cardiovascular Nursing Care", "Gastrointestinal Nursing")
                        ),
                        SyllabusSubject(
                            name = "Professional Values & Ethics in Nursing",
                            shortName = "Ethics",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf("Code of Ethics & Professional Conduct", "Legal Aspects in Nursing Practice", "Patient Rights & Informed Consent")
                        ),
                        SyllabusSubject(
                            name = "Applied Microbiology & Infection Control",
                            shortName = "Microbiology",
                            colorHex = "#EC4899",
                            iconType = "microscope",
                            suggestedTopics = listOf("Bacteriology & Virology", "Sterilization Protocols & Autoclaving", "Hospital Acquired Infections (HAI)", "Standard Precautions")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "3rd Year",
                    yearOrder = 3,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Child Health Nursing (Pediatrics)",
                            shortName = "Child Health",
                            colorHex = "#EC4899",
                            iconType = "heart",
                            suggestedTopics = listOf("Growth & Development Assessment", "Care of Normal & Sick Newborn", "Common Pediatric Illnesses & IMNCI", "Pediatric Medication Administration")
                        ),
                        SyllabusSubject(
                            name = "Mental Health Nursing (Psychiatry)",
                            shortName = "Mental Health",
                            colorHex = "#8B5CF6",
                            iconType = "cross",
                            suggestedTopics = listOf("Therapeutic Nurse-Patient Relationship", "Schizophrenia & Mood Disorders", "Anxiety, Phobias & OCD", "Crisis Intervention & Suicide Prevention")
                        ),
                        SyllabusSubject(
                            name = "Community Health Nursing I",
                            shortName = "Community 1",
                            colorHex = "#14B8A6",
                            iconType = "cross",
                            suggestedTopics = listOf("Primary Health Care Concepts", "Community Health Assessment", "Epidemiology & Communicable Disease Control", "Family Health Nursing")
                        ),
                        SyllabusSubject(
                            name = "Nursing Education & Technology",
                            shortName = "Education",
                            colorHex = "#F59E0B",
                            iconType = "book",
                            suggestedTopics = listOf("Teaching-Learning Methods in Nursing", "Clinical Teaching Methods", "Evaluation & Assessment Tools")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "4th Year / Final Year",
                    yearOrder = 4,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Midwifery & Obstetrical Nursing",
                            shortName = "Midwifery",
                            colorHex = "#F43F5E",
                            iconType = "cross",
                            suggestedTopics = listOf("Antenatal Assessment & High-Risk Management", "Conduct of Normal Delivery & Episiotomy", "Management of Obstetric Emergencies (Eclampsia, PPH)", "Postnatal Care & Lactation Support")
                        ),
                        SyllabusSubject(
                            name = "Community Health Nursing II",
                            shortName = "Community 2",
                            colorHex = "#14B8A6",
                            iconType = "cross",
                            suggestedTopics = listOf("National Health Mission & Programs", "School Health & Occupational Health Services", "Disaster Nursing Management", "Health Administration in Rural Centers")
                        ),
                        SyllabusSubject(
                            name = "Nursing Research & Statistics",
                            shortName = "Research",
                            colorHex = "#3B82F6",
                            iconType = "book",
                            suggestedTopics = listOf("Research Process in Nursing", "Data Collection Tools & Sampling", "Statistical Analysis & Nursing Evidence-Based Practice")
                        ),
                        SyllabusSubject(
                            name = "Management of Nursing Services & Leadership",
                            shortName = "Management",
                            colorHex = "#6366F1",
                            iconType = "book",
                            suggestedTopics = listOf("Hospital Organization & Ward Administration", "Staffing, Duty Rosters & Supervision", "Quality Assurance & Accreditation in Nursing")
                        )
                    )
                )
            )
        ),

        // ==========================================
        // 6. PHARMACY (B.Pharm - PCI)
        // ==========================================
        CourseCurriculum(
            course = MedicalCourse.PHARMACY,
            years = listOf(
                YearCurriculum(
                    yearName = "1st Year",
                    yearOrder = 1,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Human Anatomy & Physiology I & II",
                            shortName = "HAP",
                            colorHex = "#F59E0B",
                            iconType = "heart",
                            suggestedTopics = listOf("Cellular Level of Organisation", "Cardiovascular & Respiratory Systems", "Nervous & Endocrine Systems", "Digestive & Urinary Systems")
                        ),
                        SyllabusSubject(
                            name = "Pharmaceutical Analysis",
                            shortName = "Analysis",
                            colorHex = "#06B6D4",
                            iconType = "flask",
                            suggestedTopics = listOf("Errors & Calibration", "Acid-Base & Non-Aqueous Titrations", "Precipitation & Complexometric Titrations", "Redox Titrations")
                        ),
                        SyllabusSubject(
                            name = "Pharmaceutics",
                            shortName = "Pharmaceutics",
                            colorHex = "#10B981",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("History of Pharmacy & Pharmacopoeias", "Dosage Forms Classification", "Prescription & Posology", "Syrups, Elixirs & Suspensions", "Suppositories")
                        ),
                        SyllabusSubject(
                            name = "Pharmaceutical Inorganic Chemistry",
                            shortName = "Inorg Chem",
                            colorHex = "#8B5CF6",
                            iconType = "flask",
                            suggestedTopics = listOf("Impurities in Pharmaceuticals & Limit Tests", "Gastrointestinal Agents (Antacids)", "Dental Products", "Radiopharmaceuticals")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "2nd Year",
                    yearOrder = 2,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Pharmaceutical Organic Chemistry II & III",
                            shortName = "Org Chem",
                            colorHex = "#EC4899",
                            iconType = "flask",
                            suggestedTopics = listOf("Benzene & Aromatic Compounds", "Phenols & Aromatic Amines", "Heterocyclic Compounds", "Stereochemistry & Isomerism")
                        ),
                        SyllabusSubject(
                            name = "Physical Pharmaceutics I & II",
                            shortName = "Physical Pharm",
                            colorHex = "#3B82F6",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("Solubility & Distribution", "States of Matter & Phase Rule", "Surface & Interfacial Tension", "Colloidal Dispersions & Rheology", "Micromeritics")
                        ),
                        SyllabusSubject(
                            name = "Pharmaceutical Microbiology",
                            shortName = "Microbiology",
                            colorHex = "#EF4444",
                            iconType = "microscope",
                            suggestedTopics = listOf("Staining Techniques & Cultivation", "Disinfection & Sterilization Methods", "Microbiological Assay of Antibiotics", "Aseptic Area Design")
                        ),
                        SyllabusSubject(
                            name = "Pharmacology I & Pharmacognosy I",
                            shortName = "Pharmacol & Cog",
                            colorHex = "#14B8A6",
                            iconType = "herb",
                            suggestedTopics = listOf("General Pharmacology & Receptors", "Drugs Acting on ANS", "Classification & Quality Control of Crude Drugs", "Plant Tissue Culture")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "3rd Year",
                    yearOrder = 3,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Medicinal Chemistry I & II",
                            shortName = "Med Chem",
                            colorHex = "#6366F1",
                            iconType = "flask",
                            suggestedTopics = listOf("Structure-Activity Relationship (SAR) of Drugs", "Antihistaminic & Antineoplastic Agents", "Cardiovascular Agents", "Antibiotics & Sulfonamides")
                        ),
                        SyllabusSubject(
                            name = "Industrial Pharmacy I",
                            shortName = "Industrial Pharm",
                            colorHex = "#10B981",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("Preformulation Studies", "Tablets: Manufacturing & Quality Control", "Capsules (Hard & Soft Gelatin)", "Parenteral Products & Ophthalmic Preparations")
                        ),
                        SyllabusSubject(
                            name = "Pharmacology II & III",
                            shortName = "Pharmacol II",
                            colorHex = "#EF4444",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("Cardiovascular Pharmacology (Antiarrhythmics)", "Autacoids & Respiratory Drugs", "Chemotherapy of Infections & Cancer", "Bioassays")
                        ),
                        SyllabusSubject(
                            name = "Pharmaceutical Jurisprudence",
                            shortName = "Jurisprudence",
                            colorHex = "#F59E0B",
                            iconType = "book",
                            suggestedTopics = listOf("Drugs and Cosmetics Act 1940 & Rules", "Pharmacy Act 1948", "Narcotic Drugs and Psychotropic Substances (NDPS) Act", "Intellectual Property Rights (IPR)")
                        )
                    )
                ),
                YearCurriculum(
                    yearName = "4th Year / Final Year",
                    yearOrder = 4,
                    subjects = listOf(
                        SyllabusSubject(
                            name = "Instrumental Methods of Analysis",
                            shortName = "Inst Analysis",
                            colorHex = "#06B6D4",
                            iconType = "flask",
                            suggestedTopics = listOf("UV-Visible Spectroscopy", "IR Spectroscopy & Flame Photometry", "Chromatography: HPLC, TLC, Gas Chromatography", "Electrophoresis")
                        ),
                        SyllabusSubject(
                            name = "Industrial Pharmacy II & NDDS",
                            shortName = "NDDS",
                            colorHex = "#8B5CF6",
                            iconType = "pharmacy",
                            suggestedTopics = listOf("Pilot Plant Scale-Up Techniques", "Technology Transfer & WHO Guidelines", "Liposomes, Nanoparticles & Transdermal Patches", "Targeted Drug Delivery")
                        ),
                        SyllabusSubject(
                            name = "Pharmacy Practice & Clinical Pharmacy",
                            shortName = "Practice",
                            colorHex = "#3B82F6",
                            iconType = "cross",
                            suggestedTopics = listOf("Hospital Pharmacy Organization", "Adverse Drug Reaction Monitoring", "Drug Interactions & Patient Counselling", "Clinical Trial Phases")
                        ),
                        SyllabusSubject(
                            name = "Biostatistics & Research Methodology",
                            shortName = "Biostatistics",
                            colorHex = "#14B8A6",
                            iconType = "book",
                            suggestedTopics = listOf("Parametric & Non-Parametric Tests", "ANOVA, Chi-Square Test", "Design of Experiments & Optimization")
                        )
                    )
                )
            )
        )
    ).associateBy { it.course.code }

    fun getCurriculum(courseCode: String): CourseCurriculum? {
        val clean = courseCode.trim().uppercase()
        return curricula[clean] ?: curricula["MBBS"]
    }

    fun getSubjectsForYear(courseCode: String, academicYear: String): List<SyllabusSubject> {
        val curr = getCurriculum(courseCode) ?: return emptyList()
        val normalizedYear = normalizeYear(academicYear)
        val matchedYear = curr.years.firstOrNull { 
            it.yearName.equals(normalizedYear, ignoreCase = true) ||
            it.yearName.contains(normalizedYear, ignoreCase = true) ||
            normalizedYear.contains(it.yearName, ignoreCase = true)
        } ?: curr.years.firstOrNull()
        return matchedYear?.subjects ?: emptyList()
    }

    fun getAllSubjects(courseCode: String): List<SyllabusSubject> {
        val curr = getCurriculum(courseCode) ?: return emptyList()
        return curr.years.flatMap { it.subjects }.distinctBy { it.name }
    }

    fun getAvailableYears(courseCode: String): List<String> {
        val curr = getCurriculum(courseCode) ?: return listOf("1st Year", "2nd Year", "3rd Year", "4th Year / Final Year")
        return curr.years.map { it.yearName }
    }

    fun normalizeYear(input: String): String {
        val trimmed = input.trim().lowercase()
        return when {
            trimmed.contains("1") || trimmed.contains("first") -> "1st Year"
            trimmed.contains("2") || trimmed.contains("second") -> "2nd Year"
            trimmed.contains("3") || trimmed.contains("third") -> "3rd Year"
            trimmed.contains("4") || trimmed.contains("fourth") || trimmed.contains("final") -> "4th Year / Final Year"
            else -> "1st Year"
        }
    }

    fun getSubjectColor(subjectName: String): androidx.compose.ui.graphics.Color {
        val clean = subjectName.trim().lowercase()
        val allSubs = curricula.values.flatMap { it.years.flatMap { y -> y.subjects } }
        val found = allSubs.firstOrNull { it.name.trim().lowercase() == clean || it.shortName.trim().lowercase() == clean }
        val hex = found?.colorHex ?: when (kotlin.math.abs(subjectName.hashCode()) % 6) {
            0 -> "#4F46E5"
            1 -> "#06B6D4"
            2 -> "#10B981"
            3 -> "#F59E0B"
            4 -> "#EF4444"
            else -> "#8B5CF6"
        }
        return try {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            androidx.compose.ui.graphics.Color(0xFF4F46E5)
        }
    }
}
