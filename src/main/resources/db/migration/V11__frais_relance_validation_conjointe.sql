-- CONF04 (Q-83) : relance de la validation conjointe DJ + DJA restée sans suite au-delà de
-- DELAI_VALIDATION_CONJOINTE jours. Nulle tant qu'aucune relance n'a eu lieu.
ALTER TABLE demande_frais ADD COLUMN date_derniere_relance TIMESTAMP;
