-- Le référentiel des agences (métier, 2026-10-03, observation 2).
--
-- Ce que la Direction Juridique a demandé : choisir l'agence dans une liste déroulante avec
-- recherche, plutôt que de saisir un code à la main. Les deux champs du dossier étaient du texte
-- libre de vingt caractères, sans la moindre vérification — le seul code présent sur les 341
-- dossiers de recette était « 5466546546 », qui ne désigne aucune agence.
--
-- D'OÙ VIENNENT CES DONNÉES, ET CE QU'ELLES VALENT. La liste fournie est un SCAN de cinq pages,
-- sans couche de texte : elle a été TRANSCRITE À LA MAIN depuis l'image. Les codes, les noms et
-- les villes ont été relus ; les adresses et téléphones, repris au mieux. **Cette table doit être
-- relue par le métier avant tout usage réel.** Un code d'agence erroné sur un dossier contentieux
-- n'est pas une coquille : c'est une affaire rattachée à la mauvaise agence, dans un système que
-- l'audit trace.
--
-- QUATRE PARTICULARITÉS DE LA SOURCE, ARBITRÉES PAR LE MÉTIER LE 2026-10-05 :
--
--   · « /// BAFOUSSAM III » n'a pas de code et n'est pas une agence : ÉCARTÉE.
--
--   · « MBALLA II » figure deux fois, sous 00108 et sous 108, avec le même responsable. Les DEUX
--     sont conservées : c'est ce que le métier a demandé, et l'écran les distingue par leur ville
--     et leur adresse — « Mballa 2 » pour l'une, « Carrefour Regie » pour l'autre.
--
--   · « 101 MEIGANGA » et « 108 MBALLA II » portent un code à trois chiffres là où les autres en
--     ont cinq. Les codes sont repris TELS QUELS : les normaliser inventerait des codes que le
--     Network Manager ne connaît pas.
--
--   · « 101 MEIGANGA » n'avait AUCUNE ville au scan. Le métier exige qu'elles en aient toutes une
--     (2026-10-05) : celle-ci est DÉDUITE du nom de l'agence — Meiganga est une ville de
--     l'Adamaoua, et dans cette liste toute agence nommée d'après une ville y est installée. C'est
--     une inférence, pas une donnée lue : à confirmer au moment de la relecture.
--
-- Le code n'est pas la clé primaire : une agence peut être renommée ou désactivée sans que les
-- dossiers qui la citent deviennent orphelins, et un identifiant technique stable sert les
-- références. Le code reste unique.

CREATE TABLE agence (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)  UNIQUE NOT NULL,
    nom        VARCHAR(150) NOT NULL,
    -- Obligatoire : deux agences peuvent porter le même nom, et c'est la ville qui les sépare
    -- d'abord. Une agence sans ville ne se distingue de son homonyme qu'à l'adresse, ce qui tient
    -- du hasard plus que de la règle (métier, 2026-10-05).
    ville      VARCHAR(100) NOT NULL,
    adresse    VARCHAR(255),
    telephone  VARCHAR(50),
    -- Désactivée plutôt que supprimée : les dossiers ouverts sous cette agence gardent un sens.
    actif      BOOLEAN NOT NULL DEFAULT TRUE
);

-- La recherche au fil de la frappe porte sur le code ET sur le nom, sans tenir compte de la casse.
CREATE INDEX idx_agence_nom ON agence (lower(nom));
CREATE INDEX idx_agence_code ON agence (code);

COMMENT ON TABLE agence IS
    'Agences de la banque. Transcrites d''un scan le 2026-10-05 : à relire par le métier.';
COMMENT ON COLUMN agence.ville IS
    'Obligatoire. Celle de l''agence 101 (MEIGANGA) est déduite de son nom, le scan ne la portant pas.';

INSERT INTO agence (code, nom, ville, adresse, telephone) VALUES
    ('00000', 'RÉGIE DU TERMINAL À CONTENEURS (RTC)', 'Douala', 'Port Autonome de Douala', NULL),
    ('00001', 'HIPPODROME', 'Yaoundé', 'Face immeuble siège de la CNPS, place de l''indépendance', '222 23 30 68'),
    ('00002', 'BONANJO', 'Douala', 'Quartier administratif Bonanjo', '233 42 68 00'),
    ('00003', 'BAFOUSSAM', 'Bafoussam', 'Marché A, à côté du Carrefour Total', '233 44 45 27'),
    ('00004', 'NEW BELL', 'Douala', 'Marché central, lieu dit Carrefour Anatole', '233 43 11 17'),
    ('00005', 'MESSA', 'Yaoundé', 'Près du dispensaire des sapeurs-pompiers', '222 23 63 26'),
    ('00007', 'BAMENDA', 'Bamenda', 'Commercial Avenue', '233 36 36 17'),
    ('00008', 'GAROUA', 'Garoua', 'Grand marché de Garoua, rue Minbang Martin', '222 27 29 38'),
    ('00010', 'RETRAITE', 'Yaoundé', 'Face au Collège de la Retraite', '222 22 33 91'),
    ('00011', 'MBALMAYO', 'Yaoundé', 'Mbalmayo', NULL),
    ('00012', 'NKONGSAMBA', 'Nkongsamba', 'Centre ville, quartier I, face Talla Voyage', '233 49 25 66'),
    ('00013', 'KOUSSERI', 'Kousseri', 'Artère principale, face au PMUC', '222 41 46 15'),
    ('00015', 'LIMBE', 'Limbé', 'Avenue Church Street, à côté de Sea Palace', '233 33 20 84'),
    ('00016', 'MBOPPI', 'Douala', 'Marché Mboppi', '233 42 17 90'),
    ('00017', 'AKWA', 'Douala', 'À côté de l''immeuble SOCAR à Akwa', '233 42 76 97'),
    ('00018', 'MAROUA', 'Maroua', 'Avenue des banques, face au Crédit Foncier', '222 29 29 84'),
    ('00019', 'NGAOUNDÉRÉ', 'Ngaoundéré', 'Centre commercial, entre Orange et MTN', '222 25 16 85'),
    ('00020', 'NDOKOTTI', 'Douala', 'Face à la base AES-SONEL', '233 41 32 45'),
    ('00021', 'ETOUDI', 'Yaoundé', 'Entre la quincaillerie Fokou et le carrefour de la Présidence', '222 20 40 65'),
    ('00022', 'BONAMOUSSADI', 'Douala', 'Près du marché, non loin de la station Tradex', '233 47 88 45'),
    ('00023', 'AKWA MILLENIUM', 'Douala', 'Centre commercial, immeuble Ancien Dalip', '233 43 14 55'),
    ('00024', 'BIYEM ASSI', 'Yaoundé', 'Carrefour Biyem Assi', '222 31 85 81'),
    ('00025', 'BESSENGUE', 'Douala', 'Bessengue', '233 41 35 07'),
    ('00026', 'MVOG MBI', 'Yaoundé', 'Entre le rond-point Mvog Mbi et le carrefour Coron', '222 30 10 49'),
    ('00027', 'BONABÉRI', 'Douala', 'Nouvelle route Bonabéri, lieu dit Grand Hangar, à côté de MICHELIN', '233 39 30 04'),
    ('00028', 'KRIBI', 'Kribi', 'Quartier Mokolo, face au commissariat de sécurité publique', '222 46 16 52'),
    ('00029', 'DAKAR', 'Douala', 'Aux encablures du marché Dakar', '233 37 54 75'),
    ('00030', 'DSCHANG', 'Dschang', 'Ancienne BIAO, face de ARICAM', '233 45 21 78'),
    ('00031', 'BERTOUA', 'Bertoua', 'Ancien Alliance Voyage', '222 24 27 82'),
    ('00032', 'MBOUDA', 'Mbouda', 'Immeuble Amour Mezam', '233 29 53 45'),
    ('00033', 'MIMBOMAN', 'Yaoundé', 'Face des Sapeurs Pompiers, entre carrefour TERMINUS et Mimboman LIBERTE', '222 22 33 91'),
    ('00034', 'MENDONG', 'Yaoundé', 'Face de l''Hôtel KAMTHE, près du Palais de justice', '670 42 93 61'),
    ('00035', 'KUMBA', 'Kumba', 'Commercial Avenue Kumba', '678 06 55 29'),
    ('00036', 'BALENG', 'Bafoussam', 'Baleng', '670 42 92 96'),
    ('00037', 'EBOLOWA', 'Ebolowa', 'Ebolowa', '670 42 92 95'),
    ('00038', 'BASTOS', 'Yaoundé', 'Immeuble DOVV Bastos', '222 23 30 68'),
    ('00039', 'ESSOS', 'Yaoundé', 'Immeuble DOVV Essos', '678 65 76 46'),
    ('00040', 'MFOUNDI', 'Yaoundé', 'Marché Mfoundi', '670 42 92 92'),
    ('00041', 'SAINT MICHEL', 'Douala', 'Nkouloulou, lieu dit dernier poteau', '699 87 47 00'),
    ('00043', 'MESSAMENDONGO', 'Yaoundé', 'Messamendongo', NULL),
    ('00044', 'CITÉ DES PALMIERS', 'Douala', 'Descente Hôpital de district de Cité des Palmiers', '675 29 36 90'),
    ('00045', 'OMNISPORT', 'Yaoundé', 'Omnisport', NULL),
    ('00046', 'MARCHE CENTRAL', 'Yaoundé', 'Marché central', NULL),
    ('00055', 'OLEMBE', 'Yaoundé', 'Face de Petrolex Olembe', NULL),
    ('00056', 'MELEN', 'Yaoundé', 'Face de la TOTAL', NULL),
    ('00059', 'MAKEPE', 'Douala', 'Rhône Poulenc, face Boulangerie Baguette Française', '651 52 88 21'),
    ('00060', 'YASSA', 'Douala', 'Yassa, face Bocom', '651 51 29 08'),
    ('00061', 'BONAPRISO', 'Douala', 'Face MTN Bonapriso', '651 51 33 47'),
    ('00062', 'MVAN', 'Yaoundé', 'Mvan', NULL),
    ('00063', 'NKOABANG', 'Yaoundé', 'Carrefour Nkoabang', NULL),
    ('00064', 'NKOLBISSON', 'Yaoundé', 'Carrefour Nkolbisson', NULL),
    ('00066', 'NDOGPASSI', 'Douala', 'Ndogpassi', '674 11 59 75'),
    ('00067', 'EDEA', 'Edéa', 'Face ENEO Edéa', '677 37 38 13'),
    ('00068', 'PORT AUTONOME DE DOUALA (PAD)', 'Douala', 'Port Autonome de Douala, à côté de la Régie du Terminal à Conteneurs', '674 98 97 41'),
    ('00070', 'LOUM', 'Loum', 'Face de la station service Camoco', '675 87 37 65'),
    ('00072', 'TAMDJA', 'Bafoussam', 'Tamdja', NULL),
    ('00074', 'KOUMASSI', 'Douala', 'Koumassi', NULL),
    ('00075', 'BIYEM-ASSI (CARREFOUR)', 'Yaoundé', 'Carrefour Biyem-Assi', '699 04 46 51'),
    ('00076', 'SANGMELIMA', 'Sangmelima', 'Sangmelima', '675 28 86 66'),
    ('00077', 'KRIBI II', 'Kribi', 'Kribi', '698 34 07 23'),
    ('00078', 'DOUCHE MUNICIPALE', 'Douala', 'Douche municipale', NULL),
    ('00079', 'MOKOLO', 'Yaoundé', 'Marché Mokolo', NULL),
    ('00080', 'BAMENDZI', 'Bafoussam', 'Bamendzi', NULL),
    ('00083', 'BEKOKO', 'Douala', 'Bekoko', '675 10 22 00'),
    ('00086', 'NKOLBONG', 'Douala', 'Nkolbong', '675 22 22 22'),
    ('00087', 'AWAE', 'Yaoundé', 'Awae escalier', '696 96 64 56'),
    ('00089', 'MAROUA II', 'Maroua', 'Carrefour Para Maroua', '696 09 92 60'),
    ('00100', 'FIRST BANK LOGPOM', 'Douala', 'Logpom', '677 76 62 64'),
    ('00102', 'BATOURI', 'Bertoua', 'Bondossi, carrefour de la poste, face centre commercial Batouri', '650 01 27 83'),
    ('00103', 'GAROUA BOULAI', 'Bertoua', 'Quartier Bethanie, carrefour 24, à côté Délégation MINEPIA', '650 01 23 95'),
    ('00105', 'BAMENDA NKWEN', 'Bamenda', 'Mile 2 Nkwen, à côté Agence Amora Mezam', '620 82 45 43'),
    ('00106', 'MBALNGONG', 'Yaoundé', 'Lieu-dit Neptune', '696 27 35 73'),
    ('00107', 'AKWA LIBERTÉ', 'Douala', 'Boulevard de la Liberté, à côté de Nice Cream', '694 59 24 45'),
    ('00108', 'MBALLA II', 'Yaoundé', 'Mballa 2', NULL),
    ('00111', 'BONASSAMA', 'Douala', 'Bonassama', '670 53 56 56'),
    ('00112', 'NKOULOULOUN', 'Douala', 'Carrefour Nkouloulou', '698 57 42 35'),
    ('00113', 'SHELL NEW BELL', 'Douala', 'Shell New Bell', NULL),
    ('00114', 'BAFIA', 'Yaoundé', 'Bafia', NULL),
    ('00115', 'ROUMDE ADJIA', 'Garoua', 'Carrefour Boulevard', '699 92 35 08'),
    ('00116', 'MABANDA', 'Douala', 'Bonaberi', '233 42 68 00'),
    ('101', 'MEIGANGA', 'Meiganga', 'Meiganga', NULL),
    ('108', 'MBALLA II', 'Yaoundé', 'Carrefour Regie', NULL);
